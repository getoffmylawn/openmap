// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/omGraphics/EditableOMPoint.java,v $
// $RCSfile: EditableOMPoint.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:49 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.omGraphics;

import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.layer.util.stateMachine.State;
import com.bbn.openmap.omGraphics.editable.*;
import com.bbn.openmap.proj.*;
import com.bbn.openmap.util.Debug;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.*;
import javax.swing.JButton;

/**
 *
 */
public class EditableOMPoint extends EditableOMGraphic 
    implements ActionListener {

    protected GrabPoint gpc;
    protected OffsetGrabPoint gpo; // offset
    
    protected OMPoint point;

    public final static String OffsetResetCmd = "OffsetResetCmd";
    public final static int CENTER_POINT_INDEX = 0;
    public final static int OFFSET_POINT_INDEX = 1;

    /**
     * Create the EditableOMPoint, setting the state machine to create
     * the point off of the gestures.  
     */
    public EditableOMPoint() {
	createGraphic(null);
    }

    /**
     * Create an EditableOMPoint with the pointType and renderType
     * parameters in the GraphicAttributes object.
     */
    public EditableOMPoint(GraphicAttributes ga) {
	createGraphic(ga);
    }

    /**
     * Create the EditableOMPoint with an OMPoint already defined, ready
     * for editing.
     *
     * @param omc OMPoint that should be edited.
     */
    public EditableOMPoint(OMPoint omc) {
	setGraphic(omc);
    }

    /**
     * Create and initialize the state machine that interprets the
     * modifying gestures/commands, as well as ititialize the grab
     * points.  Also allocates the grab point array needed by the
     * EditableOMPoint.
     */
    public void init() {
	Debug.message("eomg", "EditableOMPoint.init()");
	setCanGrabGraphic(false);
	setStateMachine(new PointStateMachine(this));
 	gPoints = new GrabPoint[2];
    }

    /**
     * Set the graphic within the state machine.  If the graphic
     * is null, then one shall be created, and located off screen
     * until the gestures driving the state machine place it on the
     * map.  
     */
    public void setGraphic(OMGraphic graphic) {
	init();
	if (graphic instanceof OMPoint) {
	    point = (OMPoint)graphic;
	    stateMachine.setSelected();
	    setGrabPoints(point);
	} else {
	    createGraphic(null);
	}
    }

    /**
     * Create and set the graphic within the state machine.  The
     * GraphicAttributes describe the type of point to create. 
     */
    public void createGraphic(GraphicAttributes ga) {
	init();
	stateMachine.setUndefined();
	int renderType = OMGraphic.RENDERTYPE_UNKNOWN;

	if (ga != null) {
	    renderType = ga.getRenderType();
	}

	if (Debug.debugging("eomg")) {
	    Debug.output("EditableOMPoint.createGraphic(): rendertype = " +
			 renderType);
	}

	switch (renderType) {
	case (OMGraphic.RENDERTYPE_LATLON):
	    point = new OMPoint(90f, -180f);
	    break;
	case (OMGraphic.RENDERTYPE_OFFSET):
	    point = new OMPoint(90f, -180f, 0, 0);
	    break;
	default:
	    point = new OMPoint(-1, -1);
	}

	if (ga != null) {
	    ga.setTo(point);
	}

	assertGrabPoints();
    }

    /**
     * Get the OMGraphic being created/modified by the EditableOMPoint.  
     */
    public OMGraphic getGraphic() {
	return point;
    }

    /**
     * Set the GrabPoint that is in the middle of being modified, as a
     * result of a mouseDragged event, or other selection process.
     */
    public void setMovingPoint(GrabPoint gp) {
	super.setMovingPoint(gp);
    }


    /**
     * Given a MouseEvent, find a GrabPoint that it is touching, and
     * set the moving point to that GrabPoint.
     *
     * @param e MouseEvent
     * @return GrabPoint that is touched by the MouseEvent, null if
     * none are.  
     */
    public GrabPoint getMovingPoint(MouseEvent e) {

	movingPoint = null;
	GrabPoint[] gb = getGrabPoints();
	int x = e.getX();
	int y = e.getY();

	for (int i = gb.length - 1; i >=0; i--) {

	    if (gb[i] != null && 
		gb[i].distance(x, y) == 0) {

		setMovingPoint(gb[i]);
		// in case the points are on top of each other, the
		// last point in the array will take precidence.
		break;
	    }
	}
	return movingPoint;
    }

    protected int lastRenderType = -1;

    /**
     * Check to make sure the grab points are not null.  If they are,
     * allocate them, and them assign them to the array. 
     */
    public void assertGrabPoints() {
	int rt = getGraphic().getRenderType();
	if (rt != lastRenderType) {
	    clearGrabPoints();
	    lastRenderType = rt;
	}

	if (gpc == null) {
	    gpc = new GrabPoint(-1, -1);
	    gPoints[CENTER_POINT_INDEX] = gpc;
	}

	if (gpo == null) {
	    gpo = new OffsetGrabPoint(-1, -1);
	    gPoints[OFFSET_POINT_INDEX] = gpo;
	    gpo.addGrabPoint(gpc);
	}
    }

    protected void clearGrabPoints() {

	gpc = null;
	gpo = null;

	gPoints[CENTER_POINT_INDEX] = gpc;
	gPoints[OFFSET_POINT_INDEX] = gpo;
    }

    /**
     * Set the grab points for the graphic provided, setting them on
     * the extents of the graphic.  Called when you want to set the
     * grab points off the location of the graphic.
     */
    public void setGrabPoints(OMGraphic graphic) {
	Debug.message("eomg", "EditableOMPoint.setGrabPoints(graphic)");
	if (!(graphic instanceof OMPoint)) {
	    return;
	}

	assertGrabPoints();

	OMPoint point = (OMPoint) graphic;
	boolean ntr = point.getNeedToRegenerate();
	int renderType = point.getRenderType();
	int lineType = point.getLineType();

	int top = 0;
	int bottom = 0;
	int left = 0;
	int right = 0;
	LatLonPoint llp;
	int latoffset = 0;
	int lonoffset = 0;

	boolean doStraight = true;

	if (ntr == false) {

	    if (renderType == OMGraphic.RENDERTYPE_LATLON || 
		renderType == OMGraphic.RENDERTYPE_OFFSET) {
		
		if (projection != null) {
		    float lon = point.getLon();
		    float lat = point.getLat();

		    llp = new LatLonPoint(lat, lon);
		    java.awt.Point p = projection.forward(llp);
		    if (renderType == OMGraphic.RENDERTYPE_LATLON) {
    			doStraight = false;
			gpc.set((int)p.getX(), (int)p.getY());
		    } else {
			latoffset = (int)p.getY();
			lonoffset = (int)p.getX();
			gpo.set(lonoffset, latoffset);
		    }
		}
	    }

		
	    if (doStraight) {
		gpc.set(lonoffset + point.getX(), latoffset + point.getY());
	    }

	    if (renderType == OMGraphic.RENDERTYPE_OFFSET) {
		gpo.updateOffsets();
	    }

	} else {
	    Debug.message("eomg", "EditableOMPoint.setGrabPoints: graphic needs to be regenerated");
	}
    }

    /**
     * Take the current location of the GrabPoints, and modify the
     * location parameters of the OMPoint with them.  Called when you
     * want the graphic to change according to the grab points.
     */
    public void setGrabPoints() {

	int renderType = point.getRenderType();
	LatLonPoint llp1;

	Debug.message("eomg", "EditableOMPoint.setGrabPoints()");

	// Do center point for lat/lon or offset points
	if (renderType == OMGraphic.RENDERTYPE_LATLON) {

	    if (projection != null) {
		//movingPoint == gpc
		llp1 = projection.inverse(gpc.getX(), gpc.getY());
		point.set(llp1.getLatitude(), llp1.getLongitude());
		// point.setNeedToRegenerate set
	    }
	}

	boolean settingOffset = getStateMachine().getState() instanceof GraphicSetOffsetState && movingPoint == gpo;

	// If the center point is moving, the offset distance changes
	if (renderType == OMGraphic.RENDERTYPE_OFFSET) {

	    llp1 = projection.inverse(gpo.getX(), gpo.getY());

	    point.setLat(llp1.getLatitude());
	    point.setLon(llp1.getLongitude());

	    if (settingOffset || movingPoint == gpc) {
		// Don't call point.setLocation because we only want to
		// setNeedToRegenerate if !settingOffset.
		point.setX(gpc.getX() - gpo.getX());
		point.setY(gpc.getY() - gpo.getY());
	    }

	    if (!settingOffset) {
		Debug.message("eomg", "EditableOMPoint: updating offset point");
		point.set(gpc.getX() - gpo.getX(),
			  gpc.getY() - gpo.getY());
	    }

	    // Set Location has reset the rendertype, but provides
	    // the convenience of setting the max and min values
	    // for us.
	    point.setRenderType(OMGraphic.RENDERTYPE_OFFSET);
	}

	// Do the point height and width for XY and OFFSET render types.
	if (renderType == OMGraphic.RENDERTYPE_XY) {
	    Debug.message("eomg", "EditableOMPoint: updating x/y point");

	    if (movingPoint == gpc) {
		point.set(gpc.getX(), gpc.getY());
	    }
	}

	if (projection != null) {
	    regenerate(projection);
	}
    }

    /**
     * Get whether a graphic can be manipulated by its edges, rather
     * than just by its grab points.
     */
    public boolean getCanGrabGraphic() {
	return false;
    }


    /**
     * Called to set the OffsetGrabPoint to the current mouse
     * location, and update the OffsetGrabPoint with all the other
     * GrabPoint locations, so everything can shift smoothly.  Should
     * also set the OffsetGrabPoint to the movingPoint.  Should be
     * called only once at the beginning of the general movement, in
     * order to set the movingPoint.  After that, redraw(e) should
     * just be called, and the movingPoint will make the adjustments
     * to the graphic that are needed.
     */
    public void move(java.awt.event.MouseEvent e) {
    }

    /**
     * Use the current projection to place the graphics on the screen.
     * Has to be called to at least assure the graphics that they are
     * ready for rendering.  Called when the graphic position changes.
     *
     * @param proj com.bbn.openmap.proj.Projection
     * @return true 
     */
    public boolean generate(Projection proj) {
	Debug.message("eomgdetail", "EditableOMPoint.generate()");
	if (point != null) point.regenerate(proj);

	for (int i = 0; i < gPoints.length; i++) {
	    GrabPoint gp = gPoints[i];
	    if (gp != null) {
		gp.generate(proj);
	    }
	}
	return true;
    }

    /**
     * Given a new projection, the grab points may need to be
     * repositioned off the current position of the graphic. Called
     * when the projection changes.
     */
    public void regenerate(Projection proj) {
	Debug.message("eomg", "EditableOMPoint.regenerate()");
	if (point != null) point.regenerate(proj);

	setGrabPoints(point);
	generate(proj);
    }

    /**
     * Draw the EditableOMPoint parts into the java.awt.Graphics
     * object.  The grab points are only rendered if the point machine
     * state is PointSelectedState.POINT_SELECTED.
     *
     * @param graphics java.awt.Graphics.
     */
    public void render(java.awt.Graphics graphics) {
	Debug.message("eomgdetail", "EditableOMPoint.render()");

	State state = getStateMachine().getState();

	if (point != null) {
	    point.setVisible(true);
	    point.render(graphics);
	    point.setVisible(false);
	} else {
	    Debug.message("eomg", "EditableOMPoint.render: null point.");
	}
	
	int renderType = point.getRenderType();

	if (state instanceof GraphicSelectedState ||
	    state instanceof GraphicEditState) {

	    for (int i = 0; i < gPoints.length; i++) {

		GrabPoint gp = gPoints[i];
		if (gp != null) {
		    if ((i == OFFSET_POINT_INDEX &&
			 renderType == OMGraphic.RENDERTYPE_OFFSET &&
			 movingPoint == gpo) || 
			
			(state instanceof GraphicSelectedState && 
			 ((i != OFFSET_POINT_INDEX && renderType != OMGraphic.RENDERTYPE_OFFSET) || 
			  (renderType == OMGraphic.RENDERTYPE_OFFSET)))
			
			) {

			gp.setVisible(true);
			gp.render(graphics);
			gp.setVisible(false);
		    }
		}
	    }
	}
    }


    /**
     * If this EditableOMGraphic has parameters that can be
     * manipulated that are independent of other EditableOMGraphic
     * types, then you can provide the widgets to control those
     * parameters here.  By default, returns the GraphicAttributes GUI
     * widgets.  If you don't want a GUI to appear when a widget is
     * being created/edited, then don't call this method from the
     * EditableOMGraphic implementation, and return a null Component
     * from getGUI.
     * @param graphicAttributes the GraphicAttributes to use to get
     * the GUI widget from to control those parameters for this EOMG.
     * @return java.awt.Component to use to control parameters for this EOMG.
     */
//      public java.awt.Component getGUI(GraphicAttributes graphicAttributes) {
//  	java.awt.Component gui = super.getGUI(graphicAttributes);
//  	if (gui != null) {
//  	    offsetReset = new JButton("Center Pointangle on Offset");
//  	    offsetReset.addActionListener(this);
//  	    offsetReset.setActionCommand(OffsetResetCmd);
//  	    if (point == null || 
//  		point.getRenderType() != OMGraphic.RENDERTYPE_OFFSET) {
//  		offsetReset.setEnabled(false);
//  	    }
//  	    if (gui instanceof java.awt.Container) {
//  		((java.awt.Container)gui).add(offsetReset);
//  	    }
//  	    return gui;
//  	} else {
//  	    return null;
//  	}
//      }

    JButton offsetReset = null;

    public void actionPerformed(ActionEvent e) {
	if (e.getActionCommand() == OffsetResetCmd && 
	    point != null ||
	    point.getRenderType() == OMGraphic.RENDERTYPE_OFFSET) {
	    // Not sure how to do this yet.
	}
    }
}



















