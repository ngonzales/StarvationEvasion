package starvationevasion.io;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* CSVReader is a quick replacement for the Apache CSV library used by the previous
 * semester's team.  It may not be completely robust.
*/
public class CSVReader
{
  /**
   * Constant indices of USState data represented in CSV file
   */
  public static final int STATE_NAME_IDX            = 0;
  public static final int STATE_TOTAL_LAND_IDX      = 1;
  public static final int STATE_TOTAL_FARM_LAND_IDX = 2;
  public static final int STATE_CITRUS_IDX          = 3;
  public static final int STATE_NON_CITRUS_IDX      = 4;
  public static final int STATE_NUTS_IDX            = 5;
  public static final int STATE_GRAIN_IDX           = 6;
  public static final int STATE_OIL_CROP_IDX        = 7;
  public static final int STATE_VEGGIE_IDX          = 8;
  public static final int STATE_SPECIALTY_CROP_IDX  = 9;
  public static final int STATE_FEED_CROP_IDX       = 10;
  public static final int STATE_FISH_IDX            = 11;
  public static final int STATE_MEAT_IDX            = 12;
  public static final int STATE_POULTRY_EGG_IDX     = 13;
  public static final int STATE_DAIRY_IDX           = 14;

  private InputStream stream;
  private BufferedReader reader;
  private String[] columns;
  private List<CSVRecord> records;

  /** Constructs a new CSVReader
  */
  public CSVReader()
  {
  }

  /** Reads CSV data from an input stream, parsing it into a record structure compatible
  ** with the Apache reader.
  ** @param stream An input stream.
  */
  public void read(final InputStream stream) throws IOException
  {
    this.stream = stream;
    this.reader = new BufferedReader(new InputStreamReader(stream));
    this.records = new ArrayList<>();

    int lineNo = 0;
    String line;
    while ((line = reader.readLine()) != null)
    { // Process the line.
      //
      String[] tokens = line.split(",");
      if (lineNo == 0) 
      { // The first line contains the column names.
        //
        columns = tokens;
      }
      else
      { Map<String, String> tokenMap = new HashMap<>();
        for (int i = 0 ; i < columns.length ; i += 1)
        { String tok = "";
          if (i < tokens.length) tok = tokens[i];
          tokenMap.put(columns[i], tok);
        }

        records.add(new CSVRecord(lineNo, tokenMap));
      }

      lineNo += 1;
    }
  }

  /** Reads CSV data from an input file, parsing it into a record structure compatible
  ** with the Apache reader.
  ** @param file An input file.
  */
  public void read(final File file) throws IOException
  {
    read(new FileInputStream(file));
  }

  /**
   * @return The list of parsed records.
  */
  public List<CSVRecord> getRecords()
  { return records;
  }

  /**
   * @return The header text parsed from the CSV file.
  */
  public String[] getHeaders()
  { return columns;
  }

  /**
   * Closes the reader.
  */
  public void close() throws IOException
  {
    reader.close();;
    stream.hashCode();
  }

  /**
   * The data structure for storing the parsed rows.
  */
  public static class CSVRecord
  {
    private int record = 0;
    private Map<String, String> tokens;

    public CSVRecord(int number, Map<String, String> tokens)
    {
      this.record = number;
      this.tokens = tokens;
    }

    /**
	 * @return the record number of this row.
	 */
    public int getRecordNumber()
    { return record;
    }

    /**
	 * @param key to lookup.
	 * @return value stored under the key.
	 */
    public String get(String key)
    { return tokens.get(key);
    }

    /**
	 * @return the map of all keys to data.
	 */
    public Map<String,String> toMap()
    {
      return tokens;
    }
  }
}
