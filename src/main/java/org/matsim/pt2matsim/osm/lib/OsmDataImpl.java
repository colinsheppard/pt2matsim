/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.osm.lib;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.QuadTree;

import java.util.*;

/**
 * @author polettif
 */
public class OsmDataImpl implements OsmData {

	private final static Logger log = Logger.getLogger(OsmData.class);

	private final Map<Id<Osm.Node>, Osm.Node> nodes = new HashMap<>();
	private final Map<Id<Osm.Way>, Osm.Way> ways = new HashMap<>();
	private final Map<Id<Osm.Relation>, Osm.Relation> relations = new HashMap<>();

	private final Map<Long, OsmFileReader.ParsedNode> parsedNodes = new HashMap<>();
	private final Map<Long, OsmFileReader.ParsedRelation> parsedRelations = new HashMap<>();
	private final Map<Long, OsmFileReader.ParsedWay> parsedWays = new HashMap<>();

	// node extent
	private double minX = Double.MAX_VALUE;
	private double minY = Double.MAX_VALUE;
	private double maxX = Double.MIN_VALUE;
	private double maxY = Double.MIN_VALUE;

	private QuadTree<Osm.Node> quadTree;

	// Filters
	private PositiveTagFilter filter = new PositiveTagFilter();

	public OsmDataImpl(PositiveTagFilter... filters) {
		for(PositiveTagFilter f : filters) {
			this.filter.mergeFilter(f);
		}
	}

	public void buildMap() {
		quadTree = new QuadTree<>(minX, minY, maxX, maxY);

		// create nodes
		for(OsmFileReader.ParsedNode pn : parsedNodes.values()) {
			Osm.Node newNode = new OsmElement.Node(pn.id, pn.coord, pn.tags);
			quadTree.put(newNode.getCoord().getX(), newNode.getCoord().getY(), newNode);
			if(nodes.put(newNode.getId(), newNode) != null) {
				throw new RuntimeException("Node id " + newNode.getId() + "already exists on map");
			}
		}

		// create ways
		for(OsmFileReader.ParsedWay pw : parsedWays.values()) {
			boolean nodesAvailable = true;
			List<Osm.Node> nodeList = new ArrayList<>();
			for(Long id : pw.nodes) {
				Osm.Node n = nodes.get(Id.create(id, Osm.Node.class));
				if(n == null) {
					nodesAvailable = false;
					break;
				} else {
					nodeList.add(n);
				}
			}

			if(nodesAvailable) {
				Osm.Way newWay = new OsmElement.Way(pw.id, nodeList, pw.tags);
				if(ways.put(newWay.getId(), newWay) != null) {
					throw new RuntimeException("Way id " + newWay.getId() + "already exists on map");
				}

				// add way to nodes
				for(Osm.Node n : nodeList) {
					((OsmElement.Node) n).addWay(newWay);
				}
			}
		}

		// create relations
		for(OsmFileReader.ParsedRelation pr : parsedRelations.values()) {
			Osm.Relation newRel = new OsmElement.Relation(pr.id, pr.tags);
			if(relations.put(newRel.getId(), newRel) != null) {
				throw new RuntimeException("Relation id " + newRel.getId() + "already exists on map");
			}
		}

		// add relation members
		for(OsmFileReader.ParsedRelation pr : parsedRelations.values()) {

			Osm.Relation currentRel = relations.get(Id.create(pr.id, Osm.Relation.class));

			Map<Osm.Element, String> memberRoles = new HashMap<>();
			List<Osm.Element> memberList = new ArrayList<>();
			for(OsmFileReader.ParsedRelationMember pMember : pr.members) {
				Osm.Element member = null;
				switch(pMember.type) {
					case NODE:
						member = nodes.get(Id.create(pMember.refId, Osm.Node.class));
						break;
					case WAY:
						member = ways.get(Id.create(pMember.refId, Osm.Way.class));
						break;
					case RELATION:
						member = relations.get(Id.create(pMember.refId, Osm.Relation.class));
						break;
				}
				// relation member might be outside of map area
				if(member != null) {
					memberList.add(member);
					memberRoles.put(member, pMember.role);
				}

				// add relations to nodes/ways/relations
				for(Osm.Element e : memberList) {
					if(e instanceof OsmElement.Node) {
						((OsmElement.Node) e).addRelation(currentRel);
					}
					if(e instanceof OsmElement.Way) {
						((OsmElement.Way) e).addRelation(currentRel);
					}
					if(e instanceof OsmElement.Relation) {
						((OsmElement.Relation) e).addRelation(currentRel);
					}
				}
			}
			((OsmElement.Relation) currentRel).setMembers(memberList, memberRoles);
		}

		// clear memory
		parsedNodes.clear();
		parsedWays.clear();
		parsedRelations.clear();
	}

	@Override
	public Map<Id<Osm.Node>, Osm.Node> getNodes() {
		return Collections.unmodifiableMap(nodes);
	}

	@Override
	public Map<Id<Osm.Way>, Osm.Way> getWays() {
		return Collections.unmodifiableMap(ways);
	}

	@Override
	public Map<Id<Osm.Relation>, Osm.Relation> getRelations() {
		return Collections.unmodifiableMap(relations);
	}

	@Override
	public void removeNode(Id<Osm.Node> id) {
		Osm.Node n = nodes.get(id);
		for(Osm.Way w : n.getWays().values()) {
			w.getNodes().remove(n);
		}
		removeMemberFromRelations(n);
		nodes.remove(id);
	}

	@Override
	public void removeWay(Id<Osm.Way> id) {
		Osm.Way w = ways.get(id);
		for(Osm.Node n : w.getNodes()) {
			n.getWays().remove(w.getId());
		}
		removeMemberFromRelations(w);
		ways.remove(id);
	}

	@Override
	public void removeRelation(Id<Osm.Relation> id) {
		Osm.Relation rel = relations.get(id);

		for(Osm.Element e : rel.getMembers()) {
			if(e instanceof OsmElement.Node) {
				((OsmElement.Node) e).getRelations().remove(rel.getId());
			}
			if(e instanceof OsmElement.Way) {
				((OsmElement.Way) e).getRelations().remove(rel.getId());
			}
			if(e instanceof OsmElement.Relation) {
				((OsmElement.Relation) e).getRelations().remove(rel.getId());
			}
		}
		removeMemberFromRelations(rel);
	}

	private void removeMemberFromRelations(Osm.Element e) {
		Collection<Osm.Relation> memberOfRelations = null;

		if(e instanceof Osm.Node) {
			memberOfRelations = ((Osm.Node) e).getRelations().values();
		}
		else if(e instanceof Osm.Way) {
			memberOfRelations = ((Osm.Way) e).getRelations().values();
		}
		else if(e instanceof Osm.Relation) {
			memberOfRelations = ((Osm.Relation) e).getRelations().values();
		}

		assert memberOfRelations != null;
		for(Osm.Relation r : memberOfRelations) {
			r.getMembers().remove(e);
		}
	}

	@Override
	public void handleNode(OsmFileReader.ParsedNode node) {
		if(filter.matches(node)) {
			parsedNodes.put(node.id, node);
			updateExtent(node.coord);
		}
	}

	private void updateExtent(Coord c) {
		if(c.getX() < minX) minX = c.getX();
		if(c.getY() < minY) minY = c.getY();
		if(c.getX() > maxX) maxX = c.getX();
		if(c.getY() > maxY) maxY = c.getY();
	}

	@Override
	public void handleRelation(OsmFileReader.ParsedRelation relation) {
		if(filter.matches(relation)) {
			parsedRelations.put(relation.id, relation);
		}
	}

	@Override
	public void handleWay(OsmFileReader.ParsedWay way) {
		if(filter.matches(way)) {
			parsedWays.put(way.id, way);
		}
	}

}
