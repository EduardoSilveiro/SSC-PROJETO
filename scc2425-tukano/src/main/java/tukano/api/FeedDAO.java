package tukano.api;

import java.util.ArrayList;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
public class FeedDAO extends Feed{
    @Id
    private String _rid; // Cosmos generated unique id of item
    private String _ts; // timestamp of the last update to the item
    public FeedDAO() {
        super();
    }
    public FeedDAO(String userId  ) {
        super( userId );
    }
     public FeedDAO(Feed feed) {
        super(feed.getUserId() );
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
    // Additional methods specific to FeedDAO (if needed) can be added here

    @Override
    public String toString() {
        return "FeedDAO{" +
                "id='" + getId() + '\'' +
                ", userId='" + getUserId() + '\'' +
                ", followers=" + getFollowers() +
                ", follows=" + getFollows() +
                ", likes=" + getLikes() +
                '}';
    }
}
