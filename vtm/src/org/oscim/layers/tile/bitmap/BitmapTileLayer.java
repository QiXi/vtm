/*
 * Copyright 2013 Hannes Janetzek
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
package org.oscim.layers.tile.bitmap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;

import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.layers.tile.MapTile;
import org.oscim.layers.tile.TileLayer;
import org.oscim.layers.tile.TileLoader;
import org.oscim.layers.tile.TileManager;
import org.oscim.layers.tile.bitmap.TileSource.FadeStep;
import org.oscim.renderer.sublayers.BitmapLayer;
import org.oscim.renderer.sublayers.Layers;
import org.oscim.utils.FastMath;
import org.oscim.view.MapView;


public class BitmapTileLayer extends TileLayer<TileLoader> {
	private static final int TIMEOUT_CONNECT = 5000;
	private static final int TIMEOUT_READ = 10000;
	protected static final String TAG = BitmapTileLayer.class.getName();

	final TileSource mTileSource;
	private final FadeStep[] mFade;

	public BitmapTileLayer(MapView mapView, TileSource tileSource) {
		super(mapView, tileSource.getZoomLevelMin(), tileSource.getZoomLevelMax(), 100);
		mTileSource = tileSource;
		mFade = mTileSource.getFadeSteps();

	}

	@Override
	public void onUpdate(MapPosition pos, boolean changed, boolean clear) {
		super.onUpdate(pos, changed, clear);

		if (mFade == null) {
			mRenderLayer.setBitmapAlpha(1);
			return;
		}

		float alpha = 0;
		for (FadeStep f : mFade) {
			if (pos.scale < f.scaleStart || pos.scale > f.scaleEnd)
				continue;

			if (f.alphaStart == f.alphaEnd) {
				alpha = f.alphaStart;
				break;
			}
			double range = f.scaleEnd / f.scaleStart;
			float a = (float)((range - (pos.scale / f.scaleStart)) / range);
			a = FastMath.clamp(a, 0, 1);
			// interpolate alpha between start and end
			alpha = a * f.alphaStart + (1 - a) * f.alphaEnd;
			break;
		}

		mRenderLayer.setBitmapAlpha(alpha);
	}

	@Override
	protected TileLoader createLoader(TileManager tm) {
		return new TileLoader(tm) {

			@Override
			protected boolean executeJob(MapTile tile) {
				URL url;
				try {
					url = mTileSource.getTileUrl(tile);
					URLConnection urlConnection = getURLConnection(url);
					InputStream inputStream = getInputStream(urlConnection);
					Bitmap bitmap = CanvasAdapter.g.decodeBitmap(inputStream);

					tile.layers = new Layers();
					BitmapLayer l = new BitmapLayer(false);
					l.setBitmap(bitmap, Tile.SIZE, Tile.SIZE);

					tile.layers.textureLayers = l;
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}

				return false;
			}

			@Override
			public void cleanup() {
			}

			private InputStream getInputStream(URLConnection urlConnection) throws IOException {
				if ("gzip".equals(urlConnection.getContentEncoding())) {
					return new GZIPInputStream(urlConnection.getInputStream());
				}
				return urlConnection.getInputStream();
			}

			private URLConnection getURLConnection(URL url) throws IOException {
				URLConnection urlConnection = url.openConnection();
				urlConnection.setConnectTimeout(TIMEOUT_CONNECT);
				urlConnection.setReadTimeout(TIMEOUT_READ);
				return urlConnection;
			}
		};
	}
}
