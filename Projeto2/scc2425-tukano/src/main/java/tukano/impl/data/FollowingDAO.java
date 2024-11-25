package tukano.impl.data;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;


@Entity
public class FollowingDAO extends Following {

    @Id
    private String _rid; // Cosmos generated unique id of item
    private String _ts; // timestamp of the last update to the item

    public FollowingDAO() {
        super();
    }

    public FollowingDAO(String follower, String followee) {
        super(follower, followee);
    }

    public FollowingDAO(Following following) {
        super(following.getFollower(), following.getFollowee());
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


}
