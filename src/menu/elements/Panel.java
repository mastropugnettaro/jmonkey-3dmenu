package menu.elements;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.util.ArrayList;
import menu.transitions.Transition;

/**
 * The menu panel is the root of a clickable 3D menu. Add menu elements to it,
 * and register the inputListener, you're ready to roll. To allow a composite
 * pattern, a panel is also a menuElement. Thus, a Panel can contain other
 * panels.
 *
 */
public class Panel extends MenuElement
{
    // A general-purpose invisible material.

    public static Material invisibleMaterial = null;
    public static Material transparentMaterial = null;
    // The menu prefix is appended to events strings
    public static String menuPrefix = "MGC";
    private InputListener inputListener = new InputListener();
    private ArrayList<MenuElement> menuElements = new ArrayList<>();
    private ArrayList<Transition> transitions = new ArrayList<>();
    private MenuElement clickedElement = null;
    private Application application;

    /**
     * Processes a click - if it's either just been pressed or just released.
     */
    public void processClick(boolean pressedOrReleased)
    {
        /* First of all, only process input if there's no transition running. */
        if (transitions.isEmpty())
        {
            // If it's a press:
            if (pressedOrReleased)
            {
                Vector3f contactPoint = new Vector3f();
                Vector3f contactNormal = new Vector3f();

                // First create a list of all leaf menu elements, to avoid calling 
                // this method on subpanels and raytracing several times.
                ArrayList<MenuElement> candidates = new ArrayList<>();
                findLeaves(candidates);

                // Then cast a ray to find which element is clicked - if there is one.
                MenuElement nodeAimed = (MenuElement) getNodeClicked(application.getCamera(), contactPoint, contactNormal, candidates.toArray(new MenuElement[0]));

                // If something was clicked, fire its process method.
                if (nodeAimed != null)
                {
                    nodeAimed.processClick(pressedOrReleased, contactPoint);
                }
                // Also store the node as the currently focused one.
                clickedElement = nodeAimed;
            } else
            {
                // If the button has been released:
                if (clickedElement != null)
                {
                    clickedElement.processClick(pressedOrReleased, null);
                    clickedElement = null;
                }
            }
        }
    }

    /**
     * Processes a mouse drag. For locating the mouse during those events, a ray
     * cast won't do, because the mouse can be out of any component.
     */
    public void processDrag()
    {
        /* First of all, only process input if there's no transition running. */
        if (transitions.isEmpty() && clickedElement != null)
        {
            // If some component is indeed dragged, get the cursor position on the Z = 0 plane.

            Vector2f click2d = application.getInputManager().getCursorPosition();
            // Then make it 3D and absolute.
            Vector3f click3d = application.getCamera().getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 0f);
            // Finally, get the direction.
            Vector3f dir = application.getCamera().getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d);

            // Next, compute intersection of the ray with the element *local* Z=0 plane.
            // To do that simply, subtract from the click3D position "enough of the
            // direction vector" to nullify its z-component, when converted to the
            // slider's local space.
            clickedElement.worldToLocal(click3d, click3d);
            clickedElement.worldToLocal(dir, dir).normalizeLocal();

            // If the direction along Z is zero, tweak it a bit.
            if (Math.abs(dir.z) < 0.0000001f)
            {
                dir.z = Math.signum(dir.z) * 0.0000001f;
            }

            click3d.subtractLocal(dir.multLocal(click3d.z / dir.z));

            clickedElement.processDrag(click3d);
        }
    }

    /**
     * Registers the menu events (clicks, mouse moves...) in the inputListener.
     * Do it once and for all.
     */
    public void register(Application application)
    {
        // (Re-)add the bindings.
        InputManager inputManager = application.getInputManager();
        // Mouse axes.
        inputManager.addMapping(menuPrefix + "MouseRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping(menuPrefix + "MouseLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping(menuPrefix + "MouseUp", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping(menuPrefix + "MouseDown", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping(menuPrefix + "MouseWheelUp", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(menuPrefix + "MouseWheelDown", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        // Mouse buttons.
        inputManager.addMapping(menuPrefix + "LButton", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(menuPrefix + "RButton", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        // Keyboard.
        inputManager.addMapping(menuPrefix + "LCtrl", new KeyTrigger(KeyInput.KEY_LCONTROL));
        inputManager.addMapping(menuPrefix + "LShift", new KeyTrigger(KeyInput.KEY_LSHIFT));

        // If the materials haven't been loaded yet, load them.
        if (invisibleMaterial == null)
        {
            invisibleMaterial = new Material(application.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            invisibleMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.FrontAndBack);

            transparentMaterial = new Material(application.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            transparentMaterial.setColor("Color", new ColorRGBA(1, 1, 1, 0.1f));
            transparentMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        }

        // Register the listeners to this panel.
        application.getInputManager().addListener(this.inputListener, menuPrefix + "MouseLeft", menuPrefix + "MouseRight",
                menuPrefix + "MouseDown", menuPrefix + "MouseUp", menuPrefix + "MouseWheelUp", menuPrefix + "MouseWheelDown", menuPrefix + "LButton", menuPrefix + "RButton", menuPrefix + "LCtrl", menuPrefix + "LShift");

        // Save the application to access various resources.
        this.application = application;
    }

    /**
     * Hooks the menu node into the input manager. Do it once to activate the
     * menu.
     */
    public void unRegister(InputManager inputManager)
    {
        application.getInputManager().removeListener(inputListener);
        application = null;
    }

    /**
     * On updating, update all children and process all transitions.
     */
    @Override
    public void update(float tpf)
    {
        // A lsit of transitions to remove if they are over.
        ArrayList<Transition> toRemove = null;
        // For each transition:
        for (Transition transition : transitions)
        {
            // Update the transition.
            transition.update(tpf);

            // If the transition is over, mark it to remove.
            if (transition.isOver())
            {
                if (toRemove == null)
                {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(transition);
            }
        }

        // Remove all finished transitions
        if (toRemove != null)
        {
            for (Transition t : toRemove)
            {
                // Finalize the transition.
                t.finish();
                // Also remove the source panel, now fully replaced by the destination.
                remove(t.getSource());
            }

            transitions.removeAll(toRemove);
        }
    }

    public void addTransition(Transition transition)
    {
        // First add the destination panel to the menu.
        add(transition.getDestination());
        // Then add the transition itself.
        transitions.add(transition);
    }

    /**
     * A panel min bound is the min - on all coordinates - of all menu elements.
     * Zero if none.
     */
    @Override
    public Vector3f getLocalMinBound()
    {
        Vector3f result = new Vector3f(0, 0, 0);
        for (MenuElement element : menuElements)
        {
            Vector3f currentBound = element.getAbsoluteMinBound();
            result.x = Math.min(result.x, currentBound.x);
            result.y = Math.min(result.y, currentBound.y);
            result.z = Math.min(result.z, currentBound.z);
        }
        return result;
    }

    /**
     * A panel max bound is the max - on all coordinates - of all menu elements.
     * Zero if none.
     */
    @Override
    public Vector3f getLocalMaxBound()
    {
        Vector3f result = new Vector3f(0, 0, 0);
        for (MenuElement element : menuElements)
        {
            Vector3f currentBound = element.getAbsoluteMaxBound();
            result.x = Math.max(result.x, currentBound.x);
            result.y = Math.max(result.y, currentBound.y);
            result.z = Math.max(result.z, currentBound.z);
        }
        return result;
    }

    /**
     * Returns the closest node, among the list passed as argument, under the
     * mouse cursor. Also stores the contact point in the given vector (if not
     * null).
     */
    public Node getNodeClicked(Camera camera, Vector3f contactPoint, Vector3f contactNormal, Node... candidates)
    {
        CollisionResults results = new CollisionResults();
        Vector2f click2d = application.getInputManager().getCursorPosition();
        Vector3f click3d = camera.getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 0f);
        Vector3f dir = camera.getWorldCoordinates(new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();

        // 2. Aim the ray from cam loc to cam direction.
        Ray ray = new Ray(click3d, dir);

        // 3. Collect intersections between Ray and Shootables in results list.
        for (Node n : candidates)
        {
            n.collideWith(ray, results);
        }

        // Retrieve results.
        if (results.size() > 0)
        {
            // If there is at least one collision, return contact point and normal.
            if (contactPoint != null)
            {
                contactPoint.set(results.getClosestCollision().getContactPoint());
            }
            if (contactNormal != null)
            {
                contactNormal.set(results.getClosestCollision().getContactNormal());
            }

            // Finally, retrieve the node that generated the collision.
            Node collidedNode = results.getClosestCollision().getGeometry().getParent();
            for (Node n : candidates)
            {
                if (collidedNode == n || collidedNode.hasAncestor(n))
                {
                    return n;
                }
            }

            return null;
        } else
        {
            return null;
        }
    }

    /**
     * This global listener wraps the analog and action listeners for the scene.
     * It then redirects the events to all menu elements and their listeners.
     */
    private class InputListener implements AnalogListener, ActionListener
    {

        private boolean leftButtonDown = false;
        private boolean ctrlDown = false;
        private boolean shiftDown = false;

        /**
         * This listener is fired on analog events (mouse displacement,
         * scrolling...)
         */
        @Override
        public void onAnalog(String name, float value, float tpf)
        {
            // If the name begins with the menu prefix, strip it and continue.
            if (name.startsWith(menuPrefix))
            {
                name = name.replace(menuPrefix, "");
                int step = 1;
                switch (name)
                {
                    case "MouseWheelUp":
                        step = -step;
                    case "MouseWheelDown":
                        break;
                    case "MouseLeft":
                    case "MouseRight":
                    case "MouseDown":
                    case "MouseUp":
                        // If the left button is pressed, it's a drag. Process it!
                        processDrag();
                        break;
                }
            }
        }

        /**
         * This listener is fired on non-analog events (keystrokes, clicks...)
         */
        @Override
        public void onAction(String name, boolean isPressed, float tpf)
        {
            // If the name begins with the menu prefix, strip it and continue.
            if (name.startsWith(menuPrefix))
            {
                name = name.replace(menuPrefix, "");
                switch (name)
                {
                    case "LShift":
                        shiftDown = isPressed;
                        break;
                    case "LCtrl":
                        ctrlDown = isPressed;
                        break;
                    case "LButton":
                        leftButtonDown = isPressed;
                        // Process the click (on or off).
                        processClick(isPressed);

                        break;
                    case "RButton":
                        // On click:
                        if (isPressed)
                        {
                        }


                        break;
                }
            }
        }
    }

    protected void findLeaves(ArrayList<MenuElement> candidates)
    {
        for (MenuElement element : menuElements)
        {
            element.findLeaves(candidates);
        }
    }

    @Override
    public void setMaterial(Material material)
    {
this.material = material;
        for (MenuElement element : menuElements)
        {
                element.setMaterial(material);
        }
    }

    public void add(MenuElement menuElement)
    {
        // If the added element has no material set, inherit that of this panel.
        if (menuElement.material == null)
        {
            menuElement.setMaterial(this.material);
        }

        attachChild(menuElement);
        menuElements.add(menuElement);
    }

    public void remove(MenuElement menuElement)
    {
        detachChild(menuElement);
        menuElements.remove(menuElement);
    }
}
