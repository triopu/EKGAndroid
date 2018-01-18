package trio.ekgandroid;

/**
 * Created by Trio Pambudi Utomo on 13/09/2016.
 */
public class DataInternet {
    //name and address string
    private String data;
    private String heartrate;

    public DataInternet() {
      /*Blank default constructor essential for Firebase*/
    }
    //Getters and setters
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHeartrate() {
        return heartrate;
    }

    public void setHeartrate(String heartrate) {
        this.heartrate = heartrate;
    }
}
