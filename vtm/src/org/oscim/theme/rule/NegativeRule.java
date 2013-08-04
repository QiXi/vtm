/*
 * Copyright 2010, 2011, 2012 mapsforge.org
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
package org.oscim.theme.rule;

import org.oscim.core.Tag;

class NegativeRule extends Rule {
	final AttributeMatcher mAttributeMatcher;

	NegativeRule(int element, int zoom, AttributeMatcher attributeMatcher) {
		super(element, zoom);

		mAttributeMatcher = attributeMatcher;
	}

	@Override
	boolean matchesTags(Tag[] tags) {
		return mAttributeMatcher.matches(tags);
	}
}
