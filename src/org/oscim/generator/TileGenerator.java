/*
 * Copyright 2012, 2013 OpenScienceMap
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.generator;

import static org.oscim.generator.JobTile.STATE_NONE;

import org.oscim.core.MercatorProjection;
import org.oscim.core.Tag;
import org.oscim.core.Tile;
import org.oscim.database.IMapDatabase;
import org.oscim.database.IMapDatabaseCallback;
import org.oscim.database.QueryResult;
import org.oscim.renderer.MapTile;
import org.oscim.renderer.WayDecorator;
import org.oscim.renderer.layer.ExtrusionLayer;
import org.oscim.renderer.layer.Layer;
import org.oscim.renderer.layer.Layers;
import org.oscim.renderer.layer.LineLayer;
import org.oscim.renderer.layer.PolygonLayer;
import org.oscim.renderer.layer.TextItem;
import org.oscim.theme.IRenderCallback;
import org.oscim.theme.RenderTheme;
import org.oscim.theme.renderinstruction.Area;
import org.oscim.theme.renderinstruction.Line;
import org.oscim.theme.renderinstruction.RenderInstruction;
import org.oscim.theme.renderinstruction.Text;
import org.oscim.view.DebugSettings;
import org.oscim.view.MapView;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.util.Log;

/**
 * @author Hannes Janetzek
 * @note
 *       1. MapWorker calls TileGenerator.execute to load a tile.
 *       2. The tile data will be loaded from current MapDatabase
 *       3. MapDatabase calls the IMapDatabaseCallback functions
 *       implemented by TileGenerator for WAY and POI items.
 *       4. these callbacks then call RenderTheme to get the matching style.
 *       5. RenderTheme calls IRenderCallback functions with style information
 *       6. Styled items become added to MapTile.layers... roughly
 */
public class TileGenerator implements IRenderCallback, IMapDatabaseCallback {

	private static String TAG = TileGenerator.class.getName();

	private static final double STROKE_INCREASE = Math.sqrt(2);
	private static final byte LAYERS = 11;

	public static final byte STROKE_MIN_ZOOM_LEVEL = 12;
	public static final byte STROKE_MAX_ZOOM_LEVEL = 17;

	private static RenderTheme renderTheme;

	private IMapDatabase mMapDatabase;

	private MapTile mCurrentTile;

	// coordinates of the currently processed way
	private float[] mCoords;
	private short[] mIndices;

	// current line layer, will be added to outline layers
	private LineLayer mCurLineLayer;

	// layer data prepared for rendering
	private Layers mLayers;

	private TextItem mLabels;

	private int mDrawingLayer;
	private int mLevels;

	private float mStrokeScale = 1.0f;

	private RenderInstruction[] mRenderInstructions = null;

	//private final String TAG_WATER = "water".intern();
	private final String TAG_BUILDING = "building".intern();

	private final MapView mMapView;

	private final Tag[] debugTagBox = { new Tag("debug", "box") };
	private final Tag[] debugTagWay = { new Tag("debug", "way") };
	private final Tag[] debugTagArea = { new Tag("debug", "area") };
	private final float[] debugBoxCoords = { 0, 0, 0, Tile.TILE_SIZE,
			Tile.TILE_SIZE, Tile.TILE_SIZE, Tile.TILE_SIZE, 0, 0, 0 };
	private final short[] debugBoxIndex = { 10 };

	private float mProjectionScaleFactor;

	private float mPoiX, mPoiY;
	private int mPriority;

	private Tag mTagEmptyName = new Tag(Tag.TAG_KEY_NAME, null, false);
	private Tag mTagName;

	private boolean mDebugDrawPolygons;
	boolean mDebugDrawUnmatched;

	public static void setRenderTheme(RenderTheme theme) {
		renderTheme = theme;
	}

	/**
	 * @param mapView
	 *            the MapView
	 */
	public TileGenerator(MapView mapView) {
		mMapView = mapView;
	}

	public void cleanup() {
	}

	public boolean executeJob(JobTile jobTile) {
		MapTile tile;

		if (mMapDatabase == null)
			return false;

		tile = mCurrentTile = (MapTile) jobTile;
		DebugSettings debugSettings = mMapView.getDebugSettings();

		mDebugDrawPolygons = !debugSettings.mDisablePolygons;
		mDebugDrawUnmatched = debugSettings.mDrawUnmatchted;

		if (tile.layers != null) {
			// should be fixed now.
			Log.d(TAG, "XXX tile already loaded " + tile + " " + tile.state);
			return false;
		}

		mLevels = TileGenerator.renderTheme.getLevels();

		// limit stroke scale at z=17
		// if (tile.zoomLevel < STROKE_MAX_ZOOM_LEVEL)
		setScaleStrokeWidth(tile.zoomLevel);
		// else
		// setScaleStrokeWidth(STROKE_MAX_ZOOM_LEVEL);

		// acount for area changes with latitude
		mProjectionScaleFactor = 0.5f + 0.5f * (
				(float) Math.sin(Math.abs(MercatorProjection
						.pixelYToLatitude(tile.pixelY, tile.zoomLevel)) * (Math.PI / 180)));

		mLayers = new Layers();

		Log.d(TAG, "loading: " + tile);
		// 180 degree angle in building
		//if ((tile.zoomLevel != 17) || (tile.tileX == 68728 && tile.tileY == 42634))

		// bad bad poly!
		// if ((tile.zoomLevel != 17) || (tile.tileX == 69767 && tile.tileY == 47236))
		//g: [X:69165, Y:42344, Z:17]
		//if ((tile.zoomLevel != 17) || (tile.tileX == 69165 && tile.tileY == 42344))

		// tram/service elements rendered as buildnig with vts
		//if ((tile.zoomLevel != 17) || (tile.tileX == 68744 && tile.tileY == 42650))

		//if ((tile.zoomLevel != 17) || (tile.tileX == 68736 && tile.tileY == 42653))

		if (mMapDatabase.executeQuery(tile, this) != QueryResult.SUCCESS) {
			//Log.d(TAG, "Failed loading: " + tile);
			mLayers.clear();
			mLayers = null;
			TextItem.release(mLabels);
			mLabels = null;

			// FIXME add STATE_FAILED?
			tile.state = STATE_NONE;
			return false;
		}

		if (debugSettings.mDrawTileFrames) {
			mTagName = new Tag("name", tile.toString(), false);
			mPoiX = Tile.TILE_SIZE >> 1;
			mPoiY = 10;
			TileGenerator.renderTheme.matchNode(this, debugTagWay, (byte) 0);

			mIndices = debugBoxIndex;
			if (MapView.enableClosePolygons)
				mIndices[0] = 8;
			else
				mIndices[0] = 10;

			mCoords = debugBoxCoords;
			mDrawingLayer = 10 * mLevels;
			TileGenerator.renderTheme.matchWay(this, debugTagBox, (byte) 0, false, true);
		}

		tile.layers = mLayers;
		tile.labels = mLabels;
		mLayers = null;
		mLabels = null;

		return true;
	}

	private static byte getValidLayer(byte layer) {
		if (layer < 0) {
			return 0;
		} else if (layer >= LAYERS) {
			return LAYERS - 1;
		} else {
			return layer;
		}
	}

	/**
	 * Sets the scale stroke factor for the given zoom level.
	 * @param zoomLevel
	 *            the zoom level for which the scale stroke factor should be
	 *            set.
	 */
	private void setScaleStrokeWidth(byte zoomLevel) {
		int zoomLevelDiff = Math.max(zoomLevel - STROKE_MIN_ZOOM_LEVEL, 0);
		mStrokeScale = (float) Math.pow(STROKE_INCREASE, zoomLevelDiff);
		if (mStrokeScale < 1)
			mStrokeScale = 1;
	}

	public void setMapDatabase(IMapDatabase mapDatabase) {
		if (mMapDatabase != null)
			mMapDatabase.close();

		mMapDatabase = mapDatabase;
		//mMapProjection = mMapDatabase.getMapProjection();
	}

	public IMapDatabase getMapDatabase() {
		return mMapDatabase;
	}

	private boolean mRenderBuildingModel;

	private boolean filterTags(Tag[] tags) {
		mRenderBuildingModel = false;

		for (int i = 0; i < tags.length; i++) {
			String key = tags[i].key;
			if (tags[i].key == Tag.TAG_KEY_NAME) {
				mTagName = tags[i];
				tags[i] = mTagEmptyName;
			} else if (mCurrentTile.zoomLevel >= 17 &&
					key == TAG_BUILDING) {
				mRenderBuildingModel = true;
			}
		}
		return true;
	}

	// ---------------- MapDatabaseCallback -----------------
	@Override
	public void renderPointOfInterest(byte layer, Tag[] tags, float latitude,
			float longitude) {

		// reset state
		mTagName = null;

		//if (mMapProjection != null) {
		//	long x = mCurrentTile.pixelX;
		//	long y = mCurrentTile.pixelY + Tile.TILE_SIZE;
		//	long z = Tile.TILE_SIZE << mCurrentTile.zoomLevel;
		//
		//	double divx, divy;
		//	long dx = (x - (z >> 1));
		//	long dy = (y - (z >> 1));
		//
		//	if (mMapProjection == WebMercator.NAME) {
		//		double div = WebMercator.f900913 / (z >> 1);
		//		// divy = f900913 / (z >> 1);
		//		mPoiX = (float) (longitude / div - dx);
		//		mPoiY = (float) (latitude / div + dy);
		//	} else {
		//		divx = 180000000.0 / (z >> 1);
		//		divy = z / PIx4;
		//		mPoiX = (float) (longitude / divx - dx);
		//		double sinLat = Math.sin(latitude * PI180);
		//		mPoiY = (float) (Math.log((1.0 + sinLat) / (1.0 - sinLat)) * divy + dy);
		//
		//		// TODO remove this, only used for mapsforge maps
		//		if (mPoiX < -10 || mPoiX > Tile.TILE_SIZE + 10 || mPoiY < -10
		//				|| mPoiY > Tile.TILE_SIZE + 10)
		//			return;
		//	}
		//} else {
		mPoiX = longitude;
		mPoiY = latitude;
		//}

		// remove tags that should not be cached in Rendertheme
		filterTags(tags);
		// Log.d(TAG, "renderPointOfInterest: " + mTagName);

		// mNodeRenderInstructions =
		TileGenerator.renderTheme.matchNode(this, tags, mCurrentTile.zoomLevel);
	}

	private boolean mClosed;

	@Override
	public void renderWay(byte layer, Tag[] tags, float[] coords, short[] indices,
			boolean closed, int prio) {

		// reset state
		mTagName = null;
		mCurLineLayer = null;

		mPriority = prio;
		mClosed = closed;

		// replace tags that should not be cached in Rendertheme (e.g. name)
		if (!filterTags(tags))
			return;

		//	mProjected = false;
		//	mSimplify = 0.5f;
		//	if (closed) {
		//		if (mCurrentTile.zoomLevel < 14)
		//			mSimplify = 0.5f;
		//		else
		//			mSimplify = 0.2f;
		//		if (tags.length == 1 && TAG_WATER == (tags[0].value))
		//			mSimplify = 0;
		//	}

		mDrawingLayer = getValidLayer(layer) * mLevels;
		mCoords = coords;
		mIndices = indices;

		mRenderInstructions = TileGenerator.renderTheme.matchWay(this, tags,
				(byte) (mCurrentTile.zoomLevel + 0), closed, true);

		if (mRenderInstructions == null && mDebugDrawUnmatched)
			debugUnmatched(closed, tags);

		mCurLineLayer = null;
	}

	private void debugUnmatched(boolean closed, Tag[] tags) {

		Log.d(TAG, "way not matched: " + tags[0] + " "
				+ (tags.length > 1 ? tags[1] : "") + " " + closed);

		mTagName = new Tag("name", tags[0].key + ":" + tags[0].value, false);

		if (closed) {
			TileGenerator.renderTheme.matchWay(this, debugTagArea, (byte) 0, true, true);
		} else {
			TileGenerator.renderTheme.matchWay(this, debugTagWay, (byte) 0, true, true);
		}
	}

	@Override
	public void renderWaterBackground() {
	}

	@Override
	public boolean checkWay(Tag[] tags, boolean closed) {

		mRenderInstructions = TileGenerator.renderTheme.matchWay(this, tags,
				(byte) (mCurrentTile.zoomLevel + 0), closed, false);

		return mRenderInstructions != null;
	}

	// ----------------- RenderThemeCallback -----------------
	@Override
	public void renderWay(Line line, int level) {
		// projectToTile();

		if (line.outline && mCurLineLayer == null) {
			// TODO fix this in RenderTheme
			Log.e(TAG, "theme issue, cannot add outline: line must come before outline!");
			return;
		}
		int numLayer = (mDrawingLayer * 2) + level;

		LineLayer lineLayer = (LineLayer) mLayers.getLayer(numLayer, Layer.LINE);
		if (lineLayer == null)
			return;

		if (lineLayer.line == null) {
			lineLayer.line = line;

			float w = line.width;
			if (!line.fixed) {
				w *= mStrokeScale;
				w *= mProjectionScaleFactor;
			}
			lineLayer.width = w;
		}

		if (line.outline) {
			lineLayer.addOutline(mCurLineLayer);
			return;
		}

		lineLayer.addLine(mCoords, mIndices, mClosed);
		mCurLineLayer = lineLayer;
	}

	@Override
	public void renderArea(Area area, int level) {
		int numLayer = mDrawingLayer + level;

		if (mRenderBuildingModel) {
			//Log.d(TAG, "add buildings: " + mCurrentTile + " " + mPriority);
			if (mLayers.extrusionLayers == null)
				mLayers.extrusionLayers = new ExtrusionLayer(0);

			((ExtrusionLayer) mLayers.extrusionLayers).addBuildings(mCoords, mIndices, mPriority);

			return;
		}

		if (!mDebugDrawPolygons)
			return;

		//	if (!mProjected && !projectToTile())
		//		return;

		PolygonLayer layer = (PolygonLayer) mLayers.getLayer(numLayer, Layer.POLYGON);
		if (layer == null)
			return;

		if (layer.area == null)
			layer.area = area;

		layer.addPolygon(mCoords, mIndices);
	}

	@Override
	public void renderAreaCaption(Text text) {
		// Log.d(TAG, "renderAreaCaption: " + mTagName);

		if (mTagName == null)
			return;

		if (text.textKey == mTagEmptyName.key) {

			// TextItem t = new TextItem(mCoords[0], mCoords[1], mTagName.value,
			// text);
			TextItem t = TextItem.get().set(mCoords[0], mCoords[1], mTagName.value, text);
			t.next = mLabels;
			mLabels = t;
		}
	}

	@Override
	public void renderPointOfInterestCaption(Text text) {
		// Log.d(TAG, "renderPointOfInterestCaption: " + mPoiX + " " + mPoiY +
		// " " + mTagName);

		if (mTagName == null)
			return;

		if (text.textKey == mTagEmptyName.key) {
			TextItem t = TextItem.get().set(mPoiX, mPoiY, mTagName.value, text);
			// TextItem t = new TextItem(mPoiX, mPoiY, mTagName.value, text);
			t.next = mLabels;
			mLabels = t;
		}
	}

	@Override
	public void renderWayText(Text text) {
		// Log.d(TAG, "renderWayText: " + mTagName);

		if (mTagName == null)
			return;

		if (text.textKey == mTagEmptyName.key && mTagName.value != null) {

			mLabels = WayDecorator.renderText(mCoords, mTagName.value, text, 0,
					mIndices[0], mLabels);
		}
	}

	@Override
	public void renderPointOfInterestCircle(float radius, Paint fill, int level) {
	}

	@Override
	public void renderPointOfInterestSymbol(Bitmap bitmap) {
		// Log.d(TAG, "add symbol");

		//		if (mLayers.textureLayers == null)
		//			mLayers.textureLayers = new SymbolLayer();
		//
		//		SymbolLayer sl = (SymbolLayer) mLayers.textureLayers;
		//
		//		SymbolItem it = SymbolItem.get();
		//		it.x = mPoiX;
		//		it.y = mPoiY;
		//		it.bitmap = bitmap;
		//		it.billboard = true;
		//
		//		sl.addSymbol(it);
	}

	@Override
	public void renderAreaSymbol(Bitmap symbol) {
	}

	@Override
	public void renderWaySymbol(Bitmap symbol, boolean alignCenter, boolean repeat) {

	}

	//	// TODO move this to Projection classes
	//
	//  private String mMapProjection;
	//  private static final double PI180 = (Math.PI / 180) / 1000000.0;
	//  private static final double PIx4 = Math.PI * 4;
	//	private boolean mProjected;
	//	private float mSimplify;
	//
	//	private boolean projectToTile() {
	//		if (mProjected || mMapProjection == null)
	//			return true;
	//
	//		boolean useWebMercator = false;
	//
	//		if (mMapProjection == WebMercator.NAME)
	//			useWebMercator = true;
	//
	//		float[] coords = mCoords;
	//
	//		long x = mCurrentTile.pixelX;
	//		long y = mCurrentTile.pixelY + Tile.TILE_SIZE;
	//		long z = Tile.TILE_SIZE << mCurrentTile.zoomLevel;
	//		float min = mSimplify;
	//
	//		double divx, divy = 0;
	//		long dx = (x - (z >> 1));
	//		long dy = (y - (z >> 1));
	//
	//		if (useWebMercator) {
	//			divx = WebMercator.f900913 / (z >> 1);
	//		} else {
	//			divx = 180000000.0 / (z >> 1);
	//			divy = z / PIx4;
	//		}
	//
	//		for (int pos = 0, outPos = 0, i = 0, m = mIndices.length; i < m; i++) {
	//			int len = mIndices[i];
	//			if (len == 0)
	//				continue;
	//			if (len < 0)
	//				break;
	//
	//			int cnt = 0;
	//			float lat, lon, prevLon = 0, prevLat = 0;
	//
	//			for (int end = pos + len; pos < end; pos += 2) {
	//
	//				if (useWebMercator) {
	//					lon = (float) (coords[pos] / divx - dx);
	//					lat = (float) (coords[pos + 1] / divx + dy);
	//				} else {
	//					lon = (float) ((coords[pos]) / divx - dx);
	//					double sinLat = Math.sin(coords[pos + 1] * PI180);
	//					lat = (float) (Math.log((1.0 + sinLat) / (1.0 - sinLat)) * divy + dy);
	//				}
	//
	//				if (cnt != 0) {
	//					// drop small distance intermediate nodes
	//					if (lat == prevLat && lon == prevLon)
	//						continue;
	//
	//					if ((pos != end - 2) &&
	//							!((lat > prevLat + min || lat < prevLat - min) ||
	//							(lon > prevLon + min || lon < prevLon - min)))
	//						continue;
	//				}
	//				coords[outPos++] = prevLon = lon;
	//				coords[outPos++] = prevLat = lat;
	//
	//				cnt += 2;
	//			}
	//
	//			mIndices[i] = (short) cnt;
	//		}
	//		mProjected = true;
	//		// mProjectedResult = true;
	//		return true;
	//	}
}
