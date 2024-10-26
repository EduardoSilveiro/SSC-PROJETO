package tukano.api;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.ArrayList;

public class Feed {
    @Id
    private String id; // Identificador único do item
    private String userId;
    private ArrayList<String> followers;
    private ArrayList <String> follows;
    //SHORTS ID
    private ArrayList<String> likes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Feed(){

    }

    public Feed(String userId  ) {
        this.id = userId; // Usando userId como id
        this.followers = new ArrayList<>();
        this.follows = new ArrayList<>();
        this.likes = new ArrayList<>();
        this.userId = userId;
     }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    // Getters and setters for followers list
    public ArrayList <String> getFollowers() {
        return followers;
    }

    public void setFollowers(ArrayList <String> followers) {
        this.followers = followers;
    }

    // Getters and setters for follows list
    public ArrayList <String> getFollows() {
        return follows;
    }

    public void setFollows(ArrayList <String> follows) {
        this.follows = follows;
    }

    // Getters and setters for likes list
    public ArrayList <String> getLikes() {
        return likes;
    }

    public void setLikes(ArrayList <String> likes) {
        this.likes = likes;
    }

    // Optional: Utility methods to add/remove items from the lists
    public void addFollower(String follower) {
        this.followers.add(follower);
    }

    public void addFollow(String follow) {
        this.follows.add(follow);
    }

    public void addLike(String like) {
        this.likes.add(like);
    }

    public void removeFollower(String follower) {
        this.followers.remove(follower);
    }

    public void removeFollow(String follow) {
        this.follows.remove(follow);
    }

    public void removeLike(String like) {
        this.likes.remove(like);
    }

}
