package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UserDAO extends  User {

    @Id
    private String _rid; // Cosmos generated unique id of item
    private String _ts; // timestamp of the last update to the item
    private String userId;
    private String pwd;
    private String email;
    private String displayName;

    public UserDAO(){
        super();
    }
    public UserDAO(String userId, String pwd, String email, String displayName) {
        super(userId,pwd,email,displayName);
    }

    public UserDAO(User user) {
        super(user.userId(), user.pwd(), user.email(), user.displayName());
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
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getPwd() {
        return pwd;
    }
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String userId() {
        return userId;
    }

    public String pwd() {
        return pwd;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "User [userId=" + userId + ", pwd=" + pwd + ", email=" + email + ", displayName=" + displayName + "]";
    }

}
