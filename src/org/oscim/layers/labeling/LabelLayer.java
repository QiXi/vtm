/*
 * Copyright 2012, 2013 Hannes Janetzek
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
package org.oscim.layers.labeling;

import org.oscim.backend.input.MotionEvent;
import org.oscim.core.MapPosition;
import org.oscim.layers.InputLayer;
import org.oscim.layers.tile.TileRenderLayer;
import org.oscim.view.MapView;

import org.oscim.backend.Log;

public class LabelLayer extends InputLayer {
	private final static String TAG = LabelLayer.class.getName();
	final TextRenderLayer mTextLayer;

	public LabelLayer(MapView mapView, TileRenderLayer tileRenderLayer) {
		super(mapView);

		//mTextLayer = new org.oscim.renderer.layers.TextRenderLayer(mapView, tileRenderLayer);
		mTextLayer = new TextRenderLayer(mapView, tileRenderLayer);
		mLayer = mTextLayer;
	}

	@Override
	public void onUpdate(MapPosition mapPosition, boolean changed, boolean clear) {
		if (clear)
			mTextLayer.clearLabels();
	}

	private int multi;

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int action = e.getAction() & MotionEvent.ACTION_MASK;
		if (action == MotionEvent.ACTION_POINTER_DOWN) {
			multi++;
			mTextLayer.hold(true);
		} else if (action == MotionEvent.ACTION_POINTER_UP) {
			multi--;
			if (multi == 0)
				mTextLayer.hold(false);
		} else if (action == MotionEvent.ACTION_CANCEL) {
			multi = 0;
			Log.d(TAG, "cancel " + multi);
			mTextLayer.hold(false);
		}

		return false;
	}
}
