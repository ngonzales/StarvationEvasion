package starvationevasion.vis.controller;


import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import starvationevasion.vis.visuals.Earth;
import starvationevasion.vis.visuals.ResourceLoader;
import starvationevasion.vis.visuals.SpecialEffect;
import starvationevasion.vis.visuals.VisualizerLayout;

import java.util.Queue;
//import starvationevasion.visuals.smallevents.CropsTest;


/**
 * http://stackoverflow.com/questions/19621423/javafx-materials-bump-and-spec-maps
 * Original Author:jewelsea,http://stackoverflow.com/users/1155209/jewelsea
 * Modified by:Tess Daughton
 **/


public class EarthViewer
{
  public static Queue<Event> SIM_EVENTS;
  private  VisualizerLayout visLayout;
  public static final ResourceLoader RESOURCE_LOADER = new ResourceLoader();
  public static Earth earth;
  private final Scale SET_SIZE;
  private Group userView;
  private SpecialEffect specialEffect = new SpecialEffect();

  public EarthViewer(int smallEarthRadius, int largeEarthRadius)
  {
    earth = new Earth(smallEarthRadius, largeEarthRadius, RESOURCE_LOADER);
    SET_SIZE = new Scale();
    specialEffect.buildClouds();
    specialEffect.buildPinPoint(-40.0, 00.0);
  }

  public VisualizerLayout updateFull()
  {
    visLayout = new VisualizerLayout();
    return visLayout;
  }

  public Group updateMini()
  {
    userView = earth.getUniverse();
    userView.setScaleZ(0.3);
    userView.setScaleY(0.3);
    userView.setScaleX(0.3);
    userView.setDisable(true);
    //userView.setAutoSizeChildren(true);
    return userView;
  }

  public void updateEvents(String[] eventData)
  {}

}

