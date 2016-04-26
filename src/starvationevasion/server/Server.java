package starvationevasion.server;


/**
 * @author Javier Chavez (javierc@cs.unm.edu)
 */

import starvationevasion.common.*;
import starvationevasion.common.policies.EnumPolicy;
import starvationevasion.common.policies.PolicyCard;
import starvationevasion.server.io.HttpParse;
import starvationevasion.server.io.NetworkException;
import starvationevasion.server.io.ReadStrategy;
import starvationevasion.server.io.WriteStrategy;
import starvationevasion.server.io.formatters.Format;
import starvationevasion.server.io.strategies.*;
import starvationevasion.server.model.*;
import starvationevasion.server.model.db.Transaction;
import starvationevasion.server.model.db.Users;
import starvationevasion.server.model.db.backends.Backend;
import starvationevasion.server.model.db.backends.Sqlite;
import starvationevasion.sim.Simulator;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 */
public class Server
{
  private ServerSocket serverSocket;
  // List of all the workers
  private final HashMap<Class, LinkedList<Connector>> connections = new HashMap<>();

  private LinkedList<Process> processes = new LinkedList<>();

  private ArrayList<User> playerCache = new ArrayList<>();

  private long startNanoSec = 0;
  private long endNanoSec = 0;
  private final Simulator simulator;

  // list of ALL the users
  private final List<User> userList = new ArrayList<>();//Collections.synchronizedList(new ArrayList<>());

  private State currentState = State.LOGIN;
  private DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
  private Date date = new Date();

  // list of available regions
  private final List<EnumRegion> availableRegions = new ArrayList<>(EnumRegion.US_REGIONS.length);

  private final PolicyCard[][] _drafted = new PolicyCard[EnumRegion.US_REGIONS.length][2];

  // bool that listen for connections is looping over
  private boolean isWaiting = true;

  public static int TOTAL_HUMAN_PLAYERS = 0;
  public static int TOTAL_AI_PLAYERS = 2;
  public static int TOTAL_PLAYERS = TOTAL_HUMAN_PLAYERS + TOTAL_AI_PLAYERS;

  // Create a backend, currently sqlite
  private final Backend db = new Sqlite(Constant.DB_LOCATION);

  // Class that save User's to a database
  private final Transaction<User> userTransaction;
  private volatile long counter = 0;
  private volatile boolean isPlaying = false;
  private final ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
  private Future<Connector> connectorFuture;


  public Server (int portNumber)
  {
    connections.put(Temporary.class, new LinkedList<>());
    connections.put(Persistent.class, new LinkedList<>());

    userTransaction = new Users(db);
    Collections.addAll(availableRegions, EnumRegion.US_REGIONS);

    // get all the users from the database
    for (User user : userTransaction.getAll())
    {
      userList.add(user);
    }


    // startNanoSec = System.nanoTime();
    simulator = new Simulator();
    // simulator = null;

    try
    {
      serverSocket = new ServerSocket(portNumber);
      serverSocket.setSoTimeout(10);
    }
    catch(IOException e)
    {
      System.err.println("Server error: Opening socket failed.");
      e.printStackTrace();
      System.exit(-1);
    }

    // Mimic a chron-job that every half sec. it deletes stale workers.
    new Timer().schedule(new TimerTask()
    {
      @Override
      public void run ()
      {
        update();
      }
    }, 0, 1000);

    waitForConnection(portNumber);

  }


  /**
   * Get the time of server spawn time to a given time.
   *
   * @param curr is the current time
   *
   * @return difference time in seconds
   */
  public double getTimeDiff (long curr)
  {
    long nanoSecDiff = curr - startNanoSec;
    return nanoSecDiff / 1000000000.0;
  }


  /**
   * Wait for a connection.
   *
   * TODO: Refactor waiting to use a NON-Blocking Input/Output. Look at ServerSocketChannel
   *
   * @param port port to listen on.
   */
  private void waitForConnection (int port)
  {

    Socket potentialClient = null;
    Connector connector = null;

    while(isWaiting)
    {
      try
      {
        potentialClient = serverSocket.accept();
        System.out.println(dateFormat.format(date) + " Server: new Connection request received.");

        final Socket finalPotentialClient = potentialClient;
        connectorFuture = executorService.submit(new Callable<Connector>()
        {
          @Override
          public Connector call () throws Exception
          {
            return setStreamType(finalPotentialClient);
          }
        });

        connector = connectorFuture.get(3L, TimeUnit.SECONDS);
        if (connector == null)
        {
          potentialClient.close();
          continue;
        }

        connector.start();
        System.out.println(dateFormat.format(date) + " Server: Connected to " + potentialClient.getRemoteSocketAddress());

        connections.get(connector.getConnectionType()).add(connector);
        if (connector.getConnectionType() == Persistent.class)
        {
          userList.add(connector.getUser());
        }

      }
      catch(TimeoutException e)
      {
        connectorFuture.cancel(true);
        if (connector != null)
        {
          connector.shutdown();
        }
      }
      catch(SocketTimeoutException e)
      {
        // This occurs when no one has connected
      }
      catch(IOException | NetworkException e)
      {
        if (connector != null)
        {
          connector.shutdown();
          System.out.println(dateFormat.format(date) + " Server: Failed to complete handshake. Closing connection");
          System.out.println("\t" + e.getMessage());
        }
      }
      catch(Exception e)
      {
         e.printStackTrace();
      }
      finally
      {
        cleanConnectionList();
      }
    }
  }


  public String uptimeString ()
  {
    return String.format("%.3f", uptime());
  }

  public double uptime ()
  {
    long nanoSecDiff = System.nanoTime() - startNanoSec;
    return nanoSecDiff / 1000000000.0;
  }


  public synchronized Simulator getSimulator ()
  {
    return simulator;
  }

  public synchronized User getUserByUsername (String username)
  {
    for (User user : userList)
    {
      if (user.getUsername().equals(username))
      {
        return user;
      }
    }

    return null;
  }

  public List<User> getUserList ()
  {
    return userList;
  }

  public boolean createUser (User u)
  {
    if (u.getUsername() == null || u.getUsername().trim().isEmpty())
    {
      return false;
    }
    boolean found = userList.stream()
                            .anyMatch(user -> user.getUsername().equals(u.getUsername()));
    if (!found)
    {
      User created = userTransaction.create(u);
      if (created == null)
      {
        return false;
      }
      userList.add(created);
      return true;
    }
    return false;
  }


  public int getLoggedInCount ()
  {
    return (int) getLoggedInUsers().stream()
                                   .count();
  }

  public List<User> getLoggedInUsers ()
  {
    return userList.stream()
                   .filter(user -> user.isLoggedIn())
                   .collect(Collectors.toList());
  }

  public int getUserCount ()
  {
    return userList.size();
  }


  public void broadcast (Response response)
  {
    int i = 0;
    LinkedList<Connector> allConnections = connections.get(Persistent.class);
    try
    {
      for (; i < allConnections.size(); i++)
      {
        allConnections.get(i).send(response);
      }
    }
    catch(Exception e)
    {
      System.out.println("Error sending message " + e.getMessage());
      System.out.println(allConnections.get(i) +
                                 " User: " + allConnections.get(i).getUser().getUsername());
    }
  }

  public void killServer ()
  {
    System.out.println(dateFormat.format(date) + " Killing server.");
    isWaiting = false;

    broadcast(new ResponseFactory().build(uptime(),
                                            currentState,
                                            Type.BROADCAST, "Server will shutdown in 3 sec"));

    try
    {
      Thread.sleep(3100);
      for (Map.Entry<Class, LinkedList<Connector>> entry : connections.entrySet())
      {
        for (Connector connector : entry.getValue())
        {
          connector.shutdown();
        }
      }
    }
    catch(InterruptedException ex)
    {
      Thread.currentThread().interrupt();
    }

    System.exit(1);
  }

  public State getGameState ()
  {
      return currentState;
  }

  public void restartGame ()
  {
    stopGame();
    broadcast(new ResponseFactory().build(uptime(), currentState, Type.BROADCAST, "Game restarted."));
    for (User user : userList)
    {
      user.reset();
      user.setHand(new ArrayList<>());
      user.setPlaying(false);
    }
    playerCache = new ArrayList<>();
    simulator.init();

    for (int i = 0; i < _drafted.length; i++)
    {
      _drafted[i] = new PolicyCard[2];
    }
    isPlaying = true;
    currentState = State.LOGIN;
    broadcastStateChange();
  }

  public void stopGame ()
  {
    currentState = State.END;
    isPlaying = false;
    broadcast(new ResponseFactory().build(uptime(), currentState, Type.GAME_STATE, "Game has been stopped."));
  }

  public List<User> getPlayers ()
  {
    return playerCache;
  }

  public int getPlayerCount ()
  {
    return playerCache.size();
  }

  public synchronized boolean addPlayer (User u)
  {
    if (getPlayerCount() == TOTAL_PLAYERS || currentState.ordinal() > State.LOGIN.ordinal())
    {
      return false;
    }
    EnumRegion _region = u.getRegion();

    if (_region != null)
    {
      int loc = availableRegions.lastIndexOf(_region);
      if (loc == -1)
      {
        return false;
      }
      availableRegions.remove(loc);
      u.setPlaying(true);
      // players.add(u);
    }
    else
    {
      u.setRegion(availableRegions.get(Util.randInt(0, availableRegions.size() - 1)));
      u.setPlaying(true);
    }

    playerCache = new ArrayList<>();
    for (User user : userList)
    {
      if (user.isPlaying())
      {
        playerCache.add(user);
      }
    }
    return true;
  }

  public List<EnumRegion> getAvailableRegions ()
  {
    return availableRegions;
  }

  /**
   * Beginning of the game!!!
   *
   * Users are delt cards and world data is sent out.
   */
  private Void begin ()
  {

    ArrayList<WorldData> worldDataList = simulator.getWorldData(Constant.FIRST_DATA_YEAR,
                                                                Constant.FIRST_GAME_YEAR - 1);

    currentState = State.BEGINNING;
    broadcastStateChange();
    isPlaying = true;

    for (User user : getPlayers())
    {
      drawByUser(user);
      user.reset();
      user.getWorker().send(new ResponseFactory().build(uptime(),
                                                  user,
                                                  Type.USER));
    }

    broadcast(new ResponseFactory().build(uptime(), new Payload(worldDataList), Type.WORLD_DATA_LIST));
    draft();
    return null;
  }

  /**
   * Sets the state to drafting and schedules a new task.
   * Drafting allows for users to discard and draw new cards
   */
  private Void draft ()
  {
    currentState = State.DRAFTING;
    broadcastStateChange();

    waitAndAdvance(Server.this::vote);

    return null;
  }

  /**
   * Sets the state to vote and schedules a new draw task
   * Allows users to send votes on cards
   */
  private Void vote ()
  {
    currentState = State.VOTING;

    ArrayList<PolicyCard> _list = new ArrayList<>();
    synchronized(_drafted)
    {
      for (PolicyCard[] policyCards : _drafted)
      {
        for (PolicyCard policyCard : policyCards)
        {
          if (policyCard != null)
          {
            _list.add(policyCard);
          }
        }
      }
    }
    broadcastStateChange();
    broadcast(new ResponseFactory().build(uptime(),
                                          new Payload(_list),
                                          Type.VOTE_BALLOT));

    waitAndAdvance(Server.this::draw);
    return null;
  }


  /**
   * Draw cards for all users
   */
  private Void draw ()
  {
    ArrayList<PolicyCard> enactedPolicyCards = new ArrayList<>();
    ArrayList<PolicyCard> _list = new ArrayList<>();


    for (PolicyCard[] policyCards : _drafted)
    {
      for (PolicyCard p : policyCards)
      {
        if (p == null) continue;
        if (p.votesRequired() == 0 || p.getEnactingRegionCount() > p.votesRequired())
        {
          enactedPolicyCards.add(p);
        }
        _list.add(p);
        simulator.discard(p.getOwner(), p.getCardType());
      }
    }
    VoteData voteData = new VoteData(_list, enactedPolicyCards, _drafted);
    broadcast(new ResponseFactory().build(uptime(), voteData, Type.VOTE_RESULTS));

    currentState = State.DRAWING;
    broadcastStateChange();


    for (User user : getPlayers())
    {
      drawByUser(user);
      user.reset();

      user.getWorker().send(new ResponseFactory().build(uptime(),
                                                  user,
                                                  Type.USER));
    }

    // make sure there is no other thread calling the sim before advancing
    synchronized(simulator)
    {
      ArrayList<WorldData> worldData = simulator.nextTurn(enactedPolicyCards);
      System.out.println("There is " + enactedPolicyCards.size() + " cards being enacted.");
      broadcast(new ResponseFactory().build(uptime(), new Payload(worldData), Type.WORLD_DATA_LIST));
    }
    // clear the votes
    for (int i = 0; i < EnumRegion.US_REGIONS.length; i++)
    {
      // the 2d array is organized by regions that drafted card
      if (_drafted[i] == null) continue;
      for (int j = 0; j < 2; j++)
      {
        if (_drafted[i][j] == null) continue;
        _drafted[i][j].clearVotes();
      }
      _drafted[i] = new PolicyCard[2];
    }

    if (simulator.getCurrentYear() >= Constant.LAST_YEAR)
    {
      currentState = State.END;
      broadcastStateChange();
      isPlaying = false;
      return null;
    }

    waitAndAdvance(Server.this::draft);

    return null;
  }

  /**
   * Method that is on a timer called every 500ms
   *
   * Mainly to start the game and clean the connection list
   */
  private void update ()
  {
    if (getPlayerCount() == TOTAL_PLAYERS && currentState == State.LOGIN)
    {
      isPlaying = true;
      currentState = State.BEGINNING;
      broadcast(new ResponseFactory().build(uptime(), currentState, Type.GAME_STATE, "Game will begin in 10s"));
      waitAndAdvance(this::begin);
    }
  }


  /**
   * Handle a handshake with web client
   *
   * @param x Key received from client
   *
   * @return Hashed key that is to be given back to client for auth check.
   */
  private static String handshake (String x)
  {

    MessageDigest digest;
    byte[] one = x.getBytes();
    byte[] two = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes();
    byte[] combined = new byte[one.length + two.length];

    for (int i = 0; i < combined.length; ++i)
    {
      combined[i] = i < one.length ? one[i] : two[i - one.length];
    }

    try
    {
      digest = MessageDigest.getInstance("SHA-1");
    }
    catch(NoSuchAlgorithmException e)
    {
      e.printStackTrace();
      return "";
    }

    digest.reset();
    digest.update(combined);

    return new String(Base64.getEncoder().encode(digest.digest()));

  }

  private static byte[] asymmetricHandshake (String desKey, String clientPublicKey)
  {
    PublicKey pubKey = null;
    byte[] cipherText = null;

    try
    {
      final KeyFactory keyFact = KeyFactory.getInstance("RSA");

      byte[] clientBytes = Base64.getDecoder().decode(clientPublicKey);
      X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(clientBytes);

      pubKey = keyFact.generatePublic(x509KeySpec);

      final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, pubKey);

      cipherText = cipher.doFinal(desKey.getBytes());
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return cipherText;
  }

  /**
   * Set up the worker with proper streams
   *
   * @param s socket that is opened
   *
   * @return boolean true if web-socket
   */
  private Connector setStreamType (Socket s) throws
                                                       NoSuchAlgorithmException,
                                                       NoSuchPaddingException,
                                                       IOException,
                                                       InterruptedException
  {
    // Handling websocket
     StringBuilder reading = new StringBuilder();
    String line = "";
    String socketKey = "";
    byte[] encryptedKey = new byte[128];
    SocketReadStrategy reader = new SocketReadStrategy(s);
    SecretKey secretKey = null;
    boolean encrypted = false;
    int length = 0;
    s.setSoTimeout(3000);
    ReadStrategy discoveredReader = null;// = worker.getReader();
    WriteStrategy discoveredWriter = null;// = worker.getWriter();
    Format<?,?> formatter = null;

    HttpParse paresr = new HttpParse();

    while(true)
    {
      try
      {
        line = reader.read();
        reading.append(line);
        if (line.startsWith("Content-Length"))
        {
          String number = line.replaceAll("[^0-9]", "").trim();
          length = Integer.valueOf(number);
        }
      }
      catch(Exception e)
      {
        e.printStackTrace();
        return null;
      }

      if(line.equals("\r\n"))
      {
        break;
      }
    }

    if (length > 0)
    {
      byte[] messageBytes = new byte[length];
      reader.getStream().read(messageBytes);
      reading.append(new String(messageBytes));
    }

    paresr.parseRequest(reading.toString());

    String connectionType = paresr.getHeaderParam("Connection");
    String acceptType = paresr.getHeaderParam("Accept");


    if (connectionType.equals("Upgrade"))
    {
      System.out.println(dateFormat.format(date) + " Server: Connected to socket.");
      if (acceptType == null || acceptType.equals(DataType.JSON.toString()))
      {
        if (paresr.getHeaderParam("Upgrade") != null && paresr.getHeaderParam("Upgrade").equals("websocket"))
        {
          System.out.println("\tServer: Web client.");
          discoveredReader = new WebSocketReadStrategy(s, null);
          discoveredWriter = new WebSocketWriteStrategy(s, null);
        }
        else
        {
          System.out.println("\tServer: JSON client.");
          discoveredReader = new SocketReadStrategy(s, null);
          discoveredWriter = new SocketWriteStrategy(s, null);
        }
        discoveredWriter.setFormatter(DataType.JSON);
      }
      else if (acceptType.equals(DataType.POJO.toString()))
      {
        System.out.println("\tServer: Java client.");
        discoveredReader = new JavaObjectReadStrategy(s, null);
        discoveredWriter = new JavaObjectWriteStrategy(s, null);
        discoveredWriter.setFormatter(DataType.POJO);
      }
      else if (acceptType.equals(DataType.TEXT.toString()))
      {
        return null;
      }

      if (paresr.getHeaderParam("RSA-Socket-Key") != null)
      {
        System.out.println("\tServer: Encrypted Socket.");
        KeyGenerator keygenerator = KeyGenerator.getInstance(Constant.DATA_ALGORITHM);
        keygenerator.init(128);

        secretKey = keygenerator.generateKey();
        encryptedKey = Server.asymmetricHandshake(
                Base64.getEncoder().encodeToString(secretKey.getEncoded()),
                paresr.getHeaderParam("RSA-Socket-Key"));

        encrypted = true;
      }
      else if (paresr.getHeaderParam("Sec-WebSocket-Key") != null)
      {
        System.out.println("\tServer: Encrypted WS.");
        socketKey = Server.handshake(paresr.getHeaderParam("Sec-WebSocket-Key"));
        encrypted = true;
      }
    }
    else if (connectionType.equals("keep-alive"))
    {
      System.out.println(dateFormat.format(date) + " Server: HTTP request.");
      discoveredWriter = new HTTPWriteStrategy(s, null);

      if (acceptType.contains(DataType.JSON.toString()))
      {
        System.out.println("\tServer: JSON client.");
        discoveredWriter.setFormatter(DataType.JSON);
      }
      else
      {
        System.out.println("\tServer: HTML client.");
        discoveredWriter.setFormatter(DataType.HTML);
      }
      s.setSoTimeout(0);
      Temporary worker = new Temporary(s, this);

      worker.setWriter(discoveredWriter);
      worker.handleDirectly(paresr);
      return worker;
    }

    Persistent worker = new Persistent(s, this);
    // setting back to default blocking
    s.setSoTimeout(0);
    if (encrypted)
    {
      if (paresr.getHeaderParam("User-Agent") != null)
      {
        worker.getWriter().getStream().writeUTF("HTTP/1.1 101 Web Socket Protocol Handshake\n" +
                                                        "Upgrade: WebSocket\n" +
                                                        "Connection: Upgrade\n" +
                                                        "Sec-WebSocket-Accept: " + socketKey + "\r\n\r\n");
      }
      else
      {
        worker.getWriter().getStream().write(encryptedKey);
      }
      worker.getWriter().getStream().flush();
      worker.setReader(discoveredReader);
      worker.setWriter(discoveredWriter);
      worker.getWriter().setEncrypted(true, secretKey);
      worker.getReader().setEncrypted(true, secretKey);
    }
    else
    {
      broadcast(new ResponseFactory().build(uptime(), currentState, Type.CHAT, "Someone joined with unencrypted!"));
      worker.setReader(discoveredReader);
      worker.setWriter(discoveredWriter);
    }

    return worker;
  }

  /**
   * Cleans the connections list that have gone stale
   */
  private void cleanConnectionList ()
  {
    int con = 0;

    for (Map.Entry<Class, LinkedList<Connector>> entry : connections.entrySet())
    {
      for (int i = 0; i < entry.getValue().size(); i++)
      {
        if (!entry.getValue().get(i).isRunning())
        {
          Connector connector = entry.getValue().remove(i);
          User u = connector.getUser();
          u.setLoggedIn(false);
          if (u.isAnonymous())
          {
            userList.remove(u);
          }
          con++;
          connector.shutdown();
        }
      }
    }
    // check if any removed. Show removed count
    if (con > 0)
    {
      System.out.println(dateFormat.format(date) + " Removed " + con + " connection workers.");
    }
  }

  private void broadcastStateChange ()
  {
    getPlayers().stream().forEach(user -> user.isDone = false);
    broadcast(new ResponseFactory().build(uptime(), currentState, Type.GAME_STATE));
  }

  public static void main (String args[])
  {
    //Valid port numbers are Port numbers are 1024 through 65535.
    //  ports under 1024 are reserved for system services http, ftp, etc.
    int port = 5555; //default
    if (args.length > 0)
    {
      try
      {
        port = Integer.parseInt(args[0]);
        if (port < 1)
        {
          throw new Exception();
        }
      }
      catch(Exception e)
      {
        System.out.println("Usage: Server portNumber");
        System.exit(0);
      }
    }

    new Server(port);
  }


  public void draftCard (PolicyCard card, User u)
  {
    synchronized (_drafted)
    {
      _drafted[card.getOwner().ordinal()][u.drafts] = card;
    }
  }

  public boolean addVote (PolicyCard card, EnumRegion user)
  {
    synchronized(_drafted)
    {
      for (int i = 0; i < _drafted[card.getOwner().ordinal()].length; i++)
      {
        if (_drafted[card.getOwner().ordinal()][i] == null) continue;
        if (_drafted[card.getOwner().ordinal()][i].getCardType().equals(card.getCardType()))
        {
          _drafted[card.getOwner().ordinal()][i].addEnactingRegion(user);
          return true;
        }
      }
    }
    return false;
  }


  public void drawByUser (User user)
  {
    EnumPolicy[] _hand = simulator.drawCards(user.getRegion());
    if (_hand == null)
    {
      return;
    }
    Collections.addAll(user.getHand(), _hand);

  }


  public boolean startAI()
  {

    if (TOTAL_PLAYERS  == getPlayerCount() || processes.size() == TOTAL_AI_PLAYERS)
    {
      return false;
    }

    Process p = ServerUtil.StartAIProcess(new String[]{"java",
                                                       "-XX:+OptimizeStringConcat",
                                                       "-XX:+UseCodeCacheFlushing",
                                                       "-client",
                                                       "-classpath",
                                                       "./dist:./dist/libs/*",
                                                       "starvationevasion/ai/AI",
                                                       "foodgame.cs.unm.edu", "5555"});
    if (p != null)
    {
      processes.add(p);
    }
    return true;
  }

  public void killAI()
  {
    if (processes.size() > 0)
    {
      Process p = processes.poll();
      p.destroyForcibly();
      try
      {
        p.waitFor();
      }
      catch(InterruptedException e)
      {
        System.out.println("Interrupted and could not wait for exit code");
      }
      int val = p.exitValue();
      Payload data = new Payload();
      data.put("to-region", "ALL");
      data.put("card", "");
      data.put("text", "AI was removed.");
      data.put("from", "admin");

      System.out.println("AI removed with exit value: " + String.valueOf(val));

      broadcast(new ResponseFactory().build(uptime(), data, Type.CHAT));

    }
  }


  public void discard (User u, EnumPolicy card)
  {
    synchronized(simulator)
    {
      simulator.discard(u.getRegion(), card);
      u.getHand().remove(card);
    }
  }

  void waitAndAdvance(Callable phase)
  {
    startNanoSec = System.currentTimeMillis();
    endNanoSec = startNanoSec + currentState.getDuration();
    while(true)
    {
      if (!isPlaying)
      {
        return;
      }
      boolean allDone = getPlayers().stream().allMatch(user -> user.isDone);
      if (allDone || endNanoSec < System.currentTimeMillis())
      {
        try
        {
          phase.call();
        }
        catch(Exception e)
        {
          System.out.println("Could not advance. Stopping game.");
          System.out.println("Error: " + e.getMessage());
          stopGame();
          return;
        }
      }
    }
  }
}
