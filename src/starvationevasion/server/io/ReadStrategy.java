package starvationevasion.server.io;


import java.io.DataInputStream;
import java.io.IOException;

public interface ReadStrategy<Result>
{
  Result read () throws IOException, Exception;
  void close () throws IOException;

  DataInputStream getStream ();

  void setStream (DataInputStream inStream);
}
