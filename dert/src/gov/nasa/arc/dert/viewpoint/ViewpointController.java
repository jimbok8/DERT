package gov.nasa.arc.dert.viewpoint;

import gov.nasa.arc.dert.scene.World;
import gov.nasa.arc.dert.scene.tool.Path;
import gov.nasa.arc.dert.view.viewpoint.FlyThroughDialog;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.Vector;

import javax.swing.Timer;

import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector2;
import com.ardor3d.math.Vector3;
import com.ardor3d.scenegraph.Spatial;

/**
 * Controls the ViewpointNode with input from the InputHandler.
 *
 */
public class ViewpointController {

	// Determines if dolly/magnification is with or against scroll direction
	public static int mouseScrollDirection = -1;

	// Mouse position
	private int mouseX, mouseY;

	// In magnification mode
	private boolean isZoom;

	// Index of current viewpoint in list
	private int viewpointIndex;

	// Center of the window
	private int centerX, centerY;

	// Viewpoint list
	private Vector<ViewpointStore> viewpointList;

	// Node that carries the camera in the scene
	private ViewpointNode viewpointNode;

	// Helpers
	private Vector2 mousePos = new Vector2();
	private Vector3 pickPosition = new Vector3();
	private Vector3 pickNormal = new Vector3();

	// kinetic scrolling
	private long timestamp;
	private double velocity, amplitude;
	private double lastDx, lastDy;
	private double timeConstant = 325;

	// Fly through
	private Timer flyThroughTimer;
	private Vector<ViewpointStore> flyList;
	private int flyIndex;
	private FlyThroughDialog flyThroughDialog;
	private FlyThroughParameters flyParams;
	private DecimalFormat formatter1 = new DecimalFormat("00");
	private DecimalFormat formatter2 = new DecimalFormat("00.000");

	/**
	 * Constructor
	 */
	public ViewpointController() {
	}

	/**
	 * Set the viewpoint node
	 * 
	 * @param viewpointNode
	 */
	public void setViewpointNode(ViewpointNode viewpointNode) {
		this.viewpointNode = viewpointNode;
		viewpointIndex = -1;
	}

	/**
	 * Get the viewpoint node
	 * 
	 * @return
	 */
	public ViewpointNode getViewpointNode() {
		return (viewpointNode);
	}

	/**
	 * Perform a ray pick at the given mouse X/Y.
	 * 
	 * @param x
	 * @param y
	 * @param position
	 * @param normal
	 * @param noQuadTree
	 * @return
	 */
	public Spatial doPick(int x, int y, Vector3 position, Vector3 normal, boolean noQuadTree) {
		mousePos.set(x, y);
		Ray3 pickRay = new Ray3();
		viewpointNode.getCamera().getPickRay(mousePos, false, pickRay);
		return (World.getInstance().select(pickRay, position, normal, null, noQuadTree));
	}

	/**
	 * Enable magnify mode
	 * 
	 * @param enable
	 */
	public void enableZoom(boolean enable) {
		isZoom = enable;
	}

	/**
	 * Handle a mouse move.
	 * 
	 * @param x
	 * @param y
	 * @param dx
	 * @param dy
	 * @param button
	 * @param isControlled
	 *            control key held down for smaller movements
	 */
	public void mouseMove(int x, int y, int dx, int dy, int button, boolean isControlled) {
		if ((mouseX < 0) || (Math.abs(dx) > 100) || (Math.abs(dy) > 100)) {
			dx = 0;
			dy = 0;
		} else {
			dx = x - mouseX;
			dy = y - mouseY;
		}
		mouseX = x;
		mouseY = y;
		switch (button) {
		case 0:
			mouseX = -1;
			mouseY = -1;
			break;
		// Translating the terrain along its plane
		case 1:
			if (isControlled) {
				viewpointNode.drag(0.1 * dx, 0.1 * dy);
			} else {
				long now = System.currentTimeMillis();
				long elapsed = now - timestamp;
				timestamp = now;
				double delta = Math.sqrt(dx * dx + dy * dy);
				velocity = (100 * delta / (1 + elapsed)) * 0.8 + 0.2 * velocity;
				viewpointNode.drag(dx, dy);
				lastDx = dx;
				lastDy = dy;
			}
			break;
		// translating the terrain in the screen plane
		case 2:
			if (isControlled) {
				viewpointNode.translateInScreenPlane(-0.1 * dx, -0.1 * dy);
			} else {
				viewpointNode.translateInScreenPlane(-dx, -dy);
			}
			break;
		// rotating the terrain
		case 3:
			if (isControlled) {
				viewpointNode.rotate(0.1f * dy, 0.1f * dx);
			} else {
				viewpointNode.rotate(dy, dx);
			}
			break;
		}
	}

	/**
	 * The mouse was scrolled
	 * 
	 * @param delta
	 * @param isControlled
	 *            control key held down for smaller movements
	 */
	public void mouseScroll(int delta, boolean isControlled) {
		if (isZoom) {
			viewpointNode.magnify(-mouseScrollDirection * delta);
		} else {
			if (isControlled) {
				viewpointNode.dolly(mouseScrollDirection * 0.2 * delta);
			} else {
				viewpointNode.dolly(mouseScrollDirection * 2 * delta);
			}
			Spatial spat = doPick(centerX, centerY, pickPosition, pickNormal, false);
			if (spat != null) {
				viewpointNode.setLookAt(pickPosition);
			}
		}
	}

	/**
	 * Mouse button was pressed
	 * 
	 * @param x
	 * @param y
	 * @param mouseButton
	 * @param shiftDown
	 */
	public void mousePress(int x, int y, int mouseButton, boolean shiftDown) {
		mouseX = x;
		mouseY = y;
		timestamp = System.currentTimeMillis();
		velocity = 0;
		amplitude = 0;
	}

	/**
	 * Mouse button was released
	 * 
	 * @param x
	 * @param y
	 * @param mouseButton
	 */
	public void mouseRelease(int x, int y, int mouseButton) {
		mouseX = x;
		mouseY = y;
		if (Math.abs(velocity) > 10) {
			amplitude = 0.8 * velocity;
			timestamp = System.currentTimeMillis();
			double length = Math.sqrt(lastDx * lastDx + lastDy * lastDy);
			lastDx /= length;
			lastDy /= length;
		} else {
			amplitude = 0;
			Spatial spat = doPick(centerX, centerY, pickPosition, pickNormal, false);
			if (spat != null) {
				viewpointNode.setLookAt(pickPosition);
			}
		}
	}

	/**
	 * Update the kinetic scrolling
	 */
	public void update() {
		if (amplitude > 0) {
			long elapsed = System.currentTimeMillis() - timestamp;
			double delta = amplitude * Math.exp(-elapsed / timeConstant);
			if (Math.abs(delta) > 0.5) {
				viewpointNode.drag(lastDx * delta, lastDy * delta);
			} else {
				amplitude = 0;
				Spatial spat = doPick(centerX, centerY, pickPosition, pickNormal, false);
				if (spat != null) {
					viewpointNode.setLookAt(pickPosition);
				}
			}
		}
	}

	/**
	 * Left arrow key
	 */
	public void stepLeft() {
		viewpointNode.rotate(0, 1);
	}

	/**
	 * Right arrow key
	 */
	public void stepRight() {
		viewpointNode.rotate(0, -1);
	}

	/**
	 * Up arrow key
	 */
	public void stepUp() {
		viewpointNode.rotate(1, 0);
	}

	/**
	 * Down arrow key
	 */
	public void stepDown() {
		viewpointNode.rotate(-1, 0);
	}

	/**
	 * Set the list of viewpoints
	 * 
	 * @param viewpointList
	 */
	public void setViewpointList(Vector<ViewpointStore> viewpointList) {
		this.viewpointList = viewpointList;
	}

	/**
	 * Set the fly through parameters
	 * 
	 * @param flyParams
	 */
	public void setFlyParams(FlyThroughParameters flyParams) {
		this.flyParams = flyParams;
	}

	/**
	 * Add a viewpoint to the list
	 * 
	 * @param index
	 * @param name
	 */
	public void addViewpoint(int index, String name) {
		ViewpointStore vps = viewpointNode.getViewpoint(name);
		viewpointList.add(index, vps);
	}

	/**
	 * Remove a viewpoint from the list
	 * 
	 * @param vps
	 */
	public void removeViewpoint(ViewpointStore vps) {
		int index = viewpointList.indexOf(vps);
		viewpointList.remove(vps);
		viewpointIndex = index - 1;
		if (viewpointList.size() == 0) {
			viewpointIndex = -1;
		} else if (viewpointIndex < 0) {
			viewpointIndex = viewpointList.size() - 1;
		}
	}

	/**
	 * Go to the previous viewpoint in the list
	 */
	public void previousViewpoint() {
		if (viewpointList.size() == 0) {
			return;
		}
		viewpointIndex--;
		if (viewpointIndex < 0) {
			viewpointIndex = viewpointList.size() - 1;
		}
		viewpointNode.setViewpoint(viewpointList.get(viewpointIndex), true, true);
	}

	/**
	 * Go to the next viewpoint in the list
	 */
	public void nextViewpoint() {
		if (viewpointList.size() == 0) {
			return;
		}
		viewpointIndex++;
		if (viewpointIndex >= viewpointList.size()) {
			viewpointIndex = 0;
		}
		viewpointNode.setViewpoint(viewpointList.get(viewpointIndex), true, true);
	}

	/**
	 * Go to a specific viewpoint
	 * 
	 * @param vp
	 */
	public void gotoViewpoint(ViewpointStore vp) {
		int index = viewpointList.indexOf(vp);
		if (index < 0) {
			return;
		}
		viewpointIndex = index;
		viewpointNode.setViewpoint(vp, true, true);
	}

	/**
	 * Get the number of viewpoints
	 * 
	 * @return
	 */
	public int getViewpointListCount() {
		return (viewpointList.size());
	}

	/**
	 * Get the viewpoint list
	 * 
	 * @return
	 */
	public Vector<ViewpointStore> getViewpointList() {
		return (viewpointList);
	}

	/**
	 * Get the flythrough parameters
	 * 
	 * @return
	 */
	public FlyThroughParameters getFlyThroughParameters() {
		return (flyParams);
	}

	/**
	 * Stop flight
	 */
	public void stopFlyThrough() {
		flyThroughTimer.stop();
		flyIndex = 0;
	}

	/**
	 * Pause flight
	 */
	public void pauseFlyThrough() {
		flyThroughTimer.stop();
	}

	/**
	 * Start flight
	 */
	public void startFlyThrough() {
		if (flyThroughTimer == null) {
			flyIndex = 0;
			flyThroughTimer = new Timer(flyParams.millisPerFrame, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					viewpointNode.setViewpoint(flyList.get(flyIndex), true, false);
					double t = (flyIndex * flyParams.millisPerFrame) / 1000.0;
					int hr = (int) (t / 3600);
					t -= hr * 3600;
					int min = (int) (t / 60);
					double sec = t - (min * 60);
					flyThroughDialog.setStatus(formatter1.format(hr) + ":" + formatter1.format(min) + ":"
						+ formatter2.format(sec) + "    Frame " + flyIndex);
					flyIndex++;
					if (flyIndex == flyList.size()) {
						if (!flyParams.loop) {
							flyThroughTimer.stop();
							flyThroughDialog.enableApply(true);
						}
						flyIndex = 0;
					}
				}
			});
		}
		flyThroughTimer.setDelay(flyParams.millisPerFrame);
		flyThroughTimer.start();
	}

	/**
	 * Determine if in flight
	 * 
	 * @return
	 */
	public boolean isFlying() {
		return (flyThroughDialog != null);
	}

	/**
	 * Open the fly through dialog with a path
	 * 
	 * @param path
	 */
	public void flyThrough(Path path, Dialog owner) {
		if (flyThroughDialog == null) {
			flyThroughDialog = new FlyThroughDialog(owner, this);
			flyThroughDialog.pack();
			flyThroughDialog.setLocationRelativeTo(owner);
		}
		flyThroughDialog.setPath(path);
		flyThroughDialog.setVisible(true);
	}

	/**
	 * Close the fly through dialog
	 */
	public void closeFlyThrough() {
		if (flyThroughDialog != null) {
			flyThroughDialog.setVisible(false);
		}
		flyThroughDialog = null;
	}

	/**
	 * Fly through viewpoints
	 * 
	 * @param numInbetweens
	 * @param millis
	 * @param loop
	 */
	public void flyViewpoints(int numInbetweens, int millis, final boolean loop) {
		flyParams.numInbetweens = numInbetweens;
		flyParams.millisPerFrame = millis;
		flyParams.loop = loop;

		flyList = new Vector<ViewpointStore>();
		float lDelta = 1.0f / numInbetweens;
		int halfInbetweens = numInbetweens/2;
		float dDelta = 1.0f / (numInbetweens-halfInbetweens);
//		float dDelta = lDelta;
		ViewpointStore vps = viewpointList.get(0);
		for (int i = 1; i < viewpointList.size(); ++i) {
			float dp = 0;
			float lp = 0;
			for (int j = 0; j<numInbetweens; ++j) {
				flyList.add(vps.getInbetween(viewpointList.get(i), lp, dp));
				lp += lDelta;
				if (j >= halfInbetweens)
					dp += dDelta;
			}
			vps = viewpointList.get(i);
		}
		flyList.add(viewpointList.get(viewpointList.size() - 1));
	}

	/**
	 * Fly through path waypoints
	 * 
	 * @param path
	 * @param numInbetweens
	 * @param millis
	 * @param loop
	 * @param height
	 */
	public void flyPath(Path path, int numInbetweens, int millis, boolean loop, double height) {
		flyParams.numInbetweens = numInbetweens;
		flyParams.millisPerFrame = millis;
		flyParams.loop = loop;
		flyParams.pathHeight = height;

		flyList = new Vector<ViewpointStore>();
		float lDelta = 1.0f / numInbetweens;
		int halfInbetweens = numInbetweens/2;
		float dDelta = 1.0f / (numInbetweens-halfInbetweens);
//		float dDelta = lDelta;
		BasicCamera cam = new BasicCamera((BasicCamera) viewpointNode.getCamera());
		ViewpointStore vps = path.getWaypointViewpoint(0, height, cam, viewpointNode.getSceneBounds());
		for (int i = 1; i < path.getNumberOfPoints(); ++i) {
			ViewpointStore nextVps = path.getWaypointViewpoint(i, height, cam, viewpointNode.getSceneBounds());
			float dp = 0;
			float lp = 0;
			for (int j=0; j<numInbetweens; ++j) {				
				flyList.add(vps.getInbetween(nextVps, lp, dp));
				lp += lDelta;
				if (j >= halfInbetweens)
					dp += dDelta;
			}
			vps = nextVps;
		}
		flyList.add(vps);
	}

	/**
	 * The worldview window was resized
	 * 
	 * @param width
	 * @param height
	 */
	public void resize(int width, int height) {
		centerX = width / 2;
		centerY = height / 2;
		if (viewpointNode != null) {
			viewpointNode.resize(width, height);
		}
	}
}
