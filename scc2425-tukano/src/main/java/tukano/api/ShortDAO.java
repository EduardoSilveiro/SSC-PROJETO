package tukano.api;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import tukano.impl.Token;

@Entity
public class ShortDAO extends Short {
    @Id
    private String _rid; // Cosmos generated unique id of item
    private String _ts; // timestamp of the last update to the item

    public ShortDAO() {
        super();  // Calls the no-argument constructor from Short
    }

    // Constructor that passes all values to the parent Short class
    public ShortDAO(String shortId, String ownerId, String blobUrl, long timestamp, int totalLikes) {
        super(shortId, ownerId, blobUrl, timestamp, totalLikes);
    }

    public ShortDAO(Short shrt) {
        super(shrt.getShortId(),shrt.getOwnerId(), shrt.getBlobUrl());  // Calls the parent constructor for setting default timestamp and likes
    }
    public String get_rid() {
        return _rid;
    }

    public void set_rid(String _rid) {
        this._rid = _rid;
    }

    public String get_ts() {
        return _ts;
    }

    public void set_ts(String _ts) {
        this._ts = _ts;
    }

    public void updateFrom(Short other) {
        this.setOwnerId(other.getOwnerId());
        this.setBlobUrl(other.getBlobUrl());
        this.setTimestamp(other.getTimestamp());
        this.setTotalLikes(other.getTotalLikes());
    }




    public Short toShort() {
        return new Short(getShortId(), getOwnerId(), getBlobUrl(), getTimestamp(), getTotalLikes());
    }


    public static ShortDAO fromShort(Short shortObj) {
        return new ShortDAO(shortObj.getShortId(), shortObj.getOwnerId(), shortObj.getBlobUrl(),
                shortObj.getTimestamp(), shortObj.getTotalLikes());
    }
    public ShortDAO copyWithLikes_And_Token( long totLikes) {
        var urlWithToken = String.format("%s?token=%s", blobUrl, Token.get(blobUrl));
        System.out.println(urlWithToken);
        return new ShortDAO( shortId, ownerId, urlWithToken, timestamp, (int)totLikes);
    }
}
