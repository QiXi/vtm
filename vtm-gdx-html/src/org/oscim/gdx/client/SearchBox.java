package org.oscim.gdx.client;

import java.util.ArrayList;
import java.util.List;

import org.oscim.backend.Log;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeometryBuffer;
import org.oscim.core.MapPosition;
import org.oscim.layers.overlay.PathOverlay;
import org.oscim.view.MapView;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayNumber;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.jsonp.client.JsonpRequestBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SingleSelectionModel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class SearchBox {

	protected static final String TAG = SearchBox.class.getName();

	private static final String NOMINATIM_GLOBAL = "http://nominatim.openstreetmap.org/search?polygon_text=1&addressdetails=0&format=json&limit=25&q=";

	interface PoiData {
		public static final ProvidesKey<PoiData> KEY_PROVIDER = new ProvidesKey<PoiData>() {
			@Override
			public Object getKey(PoiData item) {
				return item == null ? null : item.getId();
			}
		};

		String getId();

		String getName();

		double getLatitude();

		double getLongitude();

		String getIcon();

		BoundingBox getBoundingBox();
	}

	final static class NominatimData extends JavaScriptObject implements
			PoiData {

		protected NominatimData() {
		}

		final static class BBox extends JsArrayNumber {
			protected BBox() {
			}
		}

		final static class Polygon extends JsArray<JsArrayNumber> {
			protected Polygon() {
			}

		}

		@Override
		public final native String getId() /*-{
			return this.osm_id;
		}-*/;

		public final native String name() /*-{
			return this.display_name;
		}-*/;

		public final native BBox getBBox() /*-{
			return this.boundingbox
		}-*/;

		public final native String getWkt() /*-{
			return this.geotext;
		}-*/;

		private final native String latitude() /*-{
			return this.lat;
		}-*/;

		private final native String longitude() /*-{
			return this.lon;
		}-*/;

		public final native String getIcon() /*-{
			return this.icon;
		}-*/;

		@Override
		public double getLatitude() {
			try {
				return Double.parseDouble(latitude());
			} catch (Exception e) {

			}
			return 0;
		}

		@Override
		public double getLongitude() {
			try {
				return Double.parseDouble(longitude());
			} catch (Exception e) {
			}
			return 0;
		}

		@Override
		public BoundingBox getBoundingBox() {
			if (getBBox() != null) {
				BBox b = getBBox();
				return new BoundingBox(b.get(0), b.get(2), b.get(1), b.get(3));
			}
			return null;
		}

		@Override
		public String getName() {
			String[] n = name().split(", ");
			if (n != null && n.length > 2)
				return n[0] + ", " + n[1] + " " + n[2];
			else if (n != null && n.length > 1)
				return n[0] + ", " + n[1];

			return name();
		}
	}

	class PoiCell extends AbstractCell<PoiData> {

		@Override
		public void render(com.google.gwt.cell.client.Cell.Context context,
				PoiData value, SafeHtmlBuilder sb) {

			// Value can be null, so do a null check..
			if (value == null) {
				return;
			}

			sb.appendHtmlConstant("<table>");

			if (value.getIcon() != null) {
				// Add the contact image.
				sb.appendHtmlConstant("<tr><td rowspan='3'>");
				sb.appendHtmlConstant("<img border=0 src=" + value.getIcon() + ">");
				sb.appendHtmlConstant("</td>");
			}

			// Add the name and address.
			sb.appendHtmlConstant("<td style='font-size:95%;'>");
			sb.appendEscaped(value.getName());
			sb.appendHtmlConstant("</td></tr>");
			//sb.appendEscaped("<tr><td>" + value.getId()+ "</td></tr>");
			sb.appendHtmlConstant("</table>");
			sb.appendHtmlConstant("<hline>");
		}
	}

	public SearchBox(final MapView mapView) {
		final Button searchButton = new Button("Search");
		final TextBox searchField = new TextBox();
		//searchField.setText("Bremen");
		final PathOverlay mOverlay = new PathOverlay(mapView, 0xCC0000FF);
		mapView.getLayerManager().add(mOverlay);

		// We can add style names to widgets
		searchButton.addStyleName("sendButton");

		RootPanel.get("nameFieldContainer").add(searchField);
		RootPanel.get("sendButtonContainer").add(searchButton);

		// Focus the cursor on the name field when the app loads
		searchField.setFocus(true);
		searchField.selectAll();

		// Create a cell to render each value in the list.
		PoiCell poiCell = new PoiCell();

		// Create a CellList that uses the cell.
		final CellList<PoiData> cellList = new CellList<PoiData>(poiCell,
				PoiData.KEY_PROVIDER);

		final SingleSelectionModel<PoiData> selectionModel = new SingleSelectionModel<PoiData>(
				PoiData.KEY_PROVIDER);
		cellList.setSelectionModel(selectionModel);

		final ScrollPanel scroller = new ScrollPanel(cellList);
		RootPanel.get("listContainer").add(scroller);

		scroller.setSize("350px", "300px");

		RootPanel.get("search").getElement().getStyle().setVisibility(Visibility.VISIBLE);
		scroller.setVisible(false);

		searchField.addFocusHandler(new FocusHandler() {
			@Override
			public void onFocus(FocusEvent event) {
				scroller.setVisible(true);
			}
		});
		selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
			public void onSelectionChange(SelectionChangeEvent event) {
				final PoiData d = selectionModel.getSelectedObject();

				mOverlay.clearPath();

				//Log.d(TAG, "selected " + d.getName() + " " + d.getLatitude() + " "
				//		+ d.getLongitude());

				BoundingBox b = d.getBoundingBox();
				if (b != null) {
					if (b.maxLatitudeE6 - b.minLatitudeE6 < 100 &&
							b.maxLongitudeE6 - b.minLongitudeE6 < 100)
						// for small bbox use zoom=16 to get an overview
						mapView.getMapViewPosition().animateTo(500, b.getCenterPoint(), 1 << 16, false);
					else
						mapView.getMapViewPosition().animateTo(b);
					if (d instanceof NominatimData && ((NominatimData) d).getWkt() != null) {
						String wkt = ((NominatimData) d).getWkt();

						WKTReader r = new WKTReader();
						GeometryBuffer g = new GeometryBuffer(1024, 10);
						try {
							r.parse(wkt, g);
						} catch (Exception e) {
							Log.d(TAG, wkt);
						}
						mOverlay.setGeom(g);

						//Log.d(TAG, "add polygon " + p.length());
					} else {
						mOverlay.addPoint(b.maxLatitudeE6, b.minLongitudeE6);
						mOverlay.addPoint(b.maxLatitudeE6, b.maxLongitudeE6);
						mOverlay.addPoint(b.minLatitudeE6, b.maxLongitudeE6);
						mOverlay.addPoint(b.minLatitudeE6, b.minLongitudeE6);
						mOverlay.addPoint(b.maxLatitudeE6, b.minLongitudeE6);
					}
					// hide overlay after 5 seconds
					mapView.postDelayed(new Runnable() {
						@Override
						public void run() {
							mOverlay.clearPath();
						}
					}, 5000);
				} else {
					MapPosition pos = new MapPosition();

					mapView.getMapViewPosition().setTilt(0);
					mapView.getMapViewPosition().setRotation(0);

					pos.setZoomLevel(13);
					pos.setPosition(d.getLatitude(), d.getLongitude());
					mapView.setMapPosition(pos);
					mapView.updateMap(true);
				}

				scroller.setVisible(false);
			}

		});

		// Create a handler for the sendButton and nameField
		class MyHandler implements ClickHandler, KeyUpHandler {

			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				sendRequest();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					sendRequest();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a
			 * response.
			 */
			private void sendRequest() {
				String textToServer = searchField.getText();
				searchButton.setEnabled(false);

				String url = URL
						.encode(NOMINATIM_GLOBAL
								+ textToServer);

				JsonpRequestBuilder builder = new JsonpRequestBuilder();
				builder.setCallbackParam("json_callback");
				builder.requestObject(url, new AsyncCallback<JsArray<NominatimData>>() {
					public void onFailure(Throwable caught) {
						Log.d(TAG, "request failed");
						searchButton.setEnabled(true);
					}

					public void onSuccess(JsArray<NominatimData> data) {
						List<PoiData> items = new ArrayList<PoiData>();
						for (int i = 0, n = data.length(); i < n; i++) {
							NominatimData d = data.get(i);
							items.add(d);
						}

						cellList.setRowCount(items.size(), true);
						cellList.setRowData(0, items);
						scroller.setVisible(true);
						searchButton.setEnabled(true);
					}
				});
			}
		}

		// Add a handler to send the name to the server
		MyHandler handler = new MyHandler();
		searchButton.addClickHandler(handler);
		searchField.addKeyUpHandler(handler);

	}

}
