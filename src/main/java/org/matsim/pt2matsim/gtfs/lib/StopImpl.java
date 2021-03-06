/* *********************************************************************** *
 * project: org.matsim.*
 * Stop.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt2matsim.gtfs.lib;

import org.matsim.api.core.v01.Coord;

public class StopImpl implements Stop {

	private final String id;
	private final Coord coord;
	private final String name;

	public StopImpl(String id, String name, Coord coord) {
		this.id = id;
		this.coord = coord;
		this.name = name;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * required attribute
	 */
	@Override
	public Coord getCoord() {
		return coord;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getName() {
		return name;
	}

}
