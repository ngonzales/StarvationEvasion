package starvationevasion.client.Driver;

//import starvationevasion.client.Logic.Client;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import starvationevasion.client.Client;
import starvationevasion.client.GUIOrig.GUI;
import starvationevasion.common.EnumRegion;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Dayloki on 11/14/2015.
 * This is the window that the users interacts with
 * Here you can select if your playing on a remote server or local,
 * Also handles login and creating new users
 */

public class LandingPage extends Application
{
  private int width=300;
  private int height=250;
  private final String WRONG_COMBO="Wrong username/password combo";
  private final String NO_HOST="Could not connect to host, try again";

  private Client client;
  GridPane root = new GridPane();
  Button singlePlayer=new Button();
  Button multiPlayer=new Button();
  Button confirm = new Button();
  Button multiConfirm = new Button();
  Label unameLabel = new Label("Username");
  TextField uname = new TextField();
  Label passwdLabel = new Label("Password");
  PasswordField passwd = new PasswordField();
  Button createUser=new Button("Create new User");
  Button seeUsers=new Button("Users");
  Button createUserWithRegion=new Button("Create with Region");
  ArrayList<EnumRegion> regions=new ArrayList<>(Arrays.asList(EnumRegion.US_REGIONS));
  ObservableList<EnumRegion> regionList= FXCollections.observableArrayList(regions);
  ComboBox comboBox=new ComboBox(regionList);

  /**
   * This is called when you create a new Application
   * @param stage
   * @throws Exception
   */
  @Override
  public void start(final Stage stage) throws Exception
  {

    stage.setTitle("Starvation Evasion");
    confirm.setText("Login");
    multiConfirm.setText("Login");
    singlePlayer.setText("Single Player");
    multiPlayer.setText("MultiPlayer");

    //Event handlers for buttons
    singlePlayer.setOnAction(actionEvent -> {
      try
      {
        client = new Client("Nathan", 2020);
        setLogin();
      }catch(Exception e)
      {
        errorMessage(NO_HOST);
      }
      });

    multiPlayer.setOnAction(e ->
    {
      client=new Client("foodgame.cs.unm.edu",5555);
      setLogin();
    });

    confirm.setOnAction(e ->
    {
      if(uname.getText().equals("")||passwd.getText().equals(""))
      {
        errorMessage(WRONG_COMBO);
      }
     else if(!client.loginToServer(uname.getText(),passwd.getText()))
     {
        errorMessage(WRONG_COMBO);
     }else
      {
        GUI gui=new GUI(client,null);
        Stage guiStage=new Stage();
        gui.start(guiStage);
        stage.close();
     }
    });

    seeUsers.setOnAction(event1 ->
    {
      client.getUsers();
    });

    createUser.setOnAction(event ->
    {
      if(!(uname.getText().equals(""))||!passwd.getText().equals(""))
      {
        if(comboBox.getValue()!=null)
        {
          client.createUser(uname.getText(),passwd.getText());
         // client.writeToServer("user_create " + uname.getText() + " " + passwd.getText()+" "+comboBox.getValue().toString());
        }else
        {
          client.createUser(uname.getText(),passwd.getText());
          //client.writeToServer("user_create " + uname.getText() + " " + passwd.getText());
        }
      }else errorMessage(WRONG_COMBO);
    });

    createUserWithRegion.setOnAction(event ->
    {
      Stage regionStage=new Stage();
      RegionChooser regionChooser=new RegionChooser(client,null);
      try
      {
        regionChooser.start(regionStage);
      } catch (Exception e)
      {
        e.printStackTrace();
      }
      stage.close();
    });

    //Sets up the initial stage
    root.setAlignment(Pos.CENTER);
    root.setHgap(10);
    root.setVgap(10);
    root.add(singlePlayer, 0, 5);
    root.add(multiPlayer, 0, 6);
    stage.setScene(new Scene(root, width, height));
    stage.show();
  }
  private void setLogin(){
    root.getChildren().clear();
    root.add(unameLabel, 0, 1);
    root.add(uname, 0, 2);
    root.add(passwdLabel, 0, 3);
    root.add(passwd, 0, 4);
    root.add(confirm,1,1);
    root.add(createUser,1,2);
    root.add(seeUsers,1,3);
    root.add(createUserWithRegion,1,4);
    root.add(comboBox,1,5);
  }
  @Override
  public void stop(){
    client.closeAll();
  }
  private void errorMessage(String message)
  {
    final Stage dialog = new Stage();
    VBox dialogVbox = new VBox(20);
    dialogVbox.getChildren().add(new Label(message));
    Scene dialogScene = new Scene(dialogVbox, 300, 80);
    dialog.setScene(dialogScene);
    dialog.setTitle("ERROR");
    dialog.show();
    Button btn = new Button();
    btn.setText("Ok");
    dialogVbox.getChildren().addAll(btn);
    btn.setOnAction(event ->
    {
      dialog.close();
    });
  }
}