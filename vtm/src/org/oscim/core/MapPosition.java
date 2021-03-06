/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2012 Hannes Janetzek
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
package org.oscim.core;

import org.oscim.utils.FastMath;

/** A MapPosition Container. */
public class MapPosition {
	public final static int MAX_ZOOMLEVEL = 20;
	public final static int MIN_ZOOMLEVEL = 2;

	/** projected position x 0..1 */
	public double x;
	/** projected position y 0..1 */
	public double y;
	/** absolute scale */
	public double scale;

	/** rotation angle */
	public float angle;
	/** perspective tile */
	public float tilt;

	// to be removed
	//  FastMath.log2((int) scale)
	public int zoomLevel;

	public MapPosition() {
		this.scale = 1;
		this.x = 0.5;
		this.y = 0.5;
		this.zoomLevel = 1;
		this.angle = 0;
	}

	public void setZoomLevel(int zoomLevel) {
		this.zoomLevel = zoomLevel;
		this.scale = 1 << zoomLevel;
	}

	public void setScale(double scale) {
		this.zoomLevel = FastMath.log2((int) scale);
		this.scale = scale;
	}

	public void setPosition(GeoPoint geoPoint){
		setPosition(geoPoint.getLatitude(), geoPoint.getLongitude());
	}

	public void setPosition(double latitude, double longitude) {
		latitude = MercatorProjection.limitLatitude(latitude);
		longitude = MercatorProjection.limitLongitude(longitude);
		this.x = MercatorProjection.longitudeToX(longitude);
		this.y = MercatorProjection.latitudeToY(latitude);
	}

	public void copy(MapPosition other) {
		this.zoomLevel = other.zoomLevel;
		this.angle = other.angle;
		this.scale = other.scale;
		this.x = other.x;
		this.y = other.y;
	}

	public double getZoomScale() {
		return scale / (1 << zoomLevel);
	}

	public GeoPoint getGeoPoint() {
		return new GeoPoint(MercatorProjection.toLatitude(y),
				MercatorProjection.toLongitude(x));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append("X: ");
		builder.append(x);
		builder.append(", Y: ");
		builder.append(y);
		builder.append(", Z: ");
		builder.append(zoomLevel);
		builder.append("]");
		builder.append("\tlat=");
		builder.append(MercatorProjection.toLatitude(y));
		builder.append(", lon=");
		builder.append(MercatorProjection.toLongitude(x));

		return builder.toString();
	}
}
