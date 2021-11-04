package com.cs506.cash_splitting.dao;
import com.cs506.cash_splitting.model.*;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Repository
public class UserDAOImpl implements UserDAO {

    @Autowired
    private EntityManager entityManager;

    @Override
    @ResponseBody
    public Object get() {
        Session currSession = entityManager.unwrap(Session.class);
        SQLQuery query = currSession.createSQLQuery("select * from userdb").addEntity(User.class);
        ;
        List<User> userList = new ArrayList<>();
        List list = query.list();
        for (Object o : list) {
            User user = (User) o;
            userList.add(user);
        }
        return userList;
    }

    @Override
    @ResponseBody
    public Object get(String username1) {
        Session currSession = entityManager.unwrap(Session.class);
        int uid = get_uid(username1);
        return currSession.get(User.class, uid);
    }

    @Override
    public boolean addOrUpdateUser(User user) {
        Session currSession = entityManager.unwrap(Session.class);
        currSession.saveOrUpdate(user);
        return true;
    }


    @Override
    public boolean addOrUpdatePassword(Password password) {
        Password record = new Password();
        int hash_password = password.getPassword().hashCode();
        record.setPassword(String.valueOf(hash_password));
        record.setUid(password.getUid());
        Session currSession = entityManager.unwrap(Session.class);
        currSession.saveOrUpdate(record);
        return true;
    }


    @Override
    public String getUserName(int uid) {
        Session currSession = entityManager.unwrap(Session.class);
        return currSession.get(User.class, uid).getUsername();
    }


    @Override
    public List<?> check(String username, String password) {
        int uid = this.get_uid(username);
        Session currSession = entityManager.unwrap(Session.class);
        int encrypt_password = password.hashCode();
        String hash_password = String.valueOf(encrypt_password);
        Query query = currSession.createSQLQuery("select password from passworddb where uid = :userid and password = :code ");
        query.setParameter("userid", uid);
        query.setParameter("code", hash_password);
        return query.getResultList();
    }


    public int get_uid(String username) {
        Session currSession = entityManager.unwrap(Session.class);
        org.hibernate.Query query = currSession.createSQLQuery("select * from userdb" +
                " where username = :name").addEntity(User.class);
        query.setParameter("name", username);
        List list = query.list();
        if (list.isEmpty()) {
            return -1;
        }
        User user = (User) list.get(0);
        return user.getUid();
    }

    @Override
    public boolean createGroup(Group group){
        Session currSession = entityManager.unwrap(Session.class);
        //sanity check
        org.hibernate.Query query = currSession.createSQLQuery("select distinct gid from groupdb " +
                "where status = 'valid'");
        List list = query.list();
        int newGid = group.getGid();
        if (!list.isEmpty() && list.contains(newGid)){
            return false; // group already exist! cannot create a new group
        }
        // if gid not exist
        currSession.saveOrUpdate(group);

        return true;
    }

    @Override
    public boolean addMember(int gid, int uid){
        Session currSession = entityManager.unwrap(Session.class);
        //sanity check
        org.hibernate.Query query = currSession.createSQLQuery("select uid from groupdb" +
                " where gid = :gid and status = 'valid'");
        query.setParameter("gid", gid);
        List list = query.list();
        if (list.isEmpty()) { // no existing group with that gid OR no valid user in that group -> 'dead group'
            return false;
        }
        if (list.contains(uid)){
            return false; // this user is already in the group
        }
        // if this user used to be in the group, status = 'invalid' now
        org.hibernate.Query query3 = currSession.createSQLQuery("select * from groupdb" +
                " where gid = :gid and uid =:uid and status = 'invalid'").addEntity(Group.class);
        query3.setParameter("gid", gid);
        query3.setParameter("uid", uid);
        List oldMemeber = query3.list();
        if (!oldMemeber.isEmpty()){
            Group entry = (Group) oldMemeber.get(0);
            entry.setStatus("valid");
            return true;
        }
        // check passed, create a new entry and add into db
        org.hibernate.Query query2 = currSession.createSQLQuery("select groupname from groupdb" +
                " where gid = :gid and status = 'valid'");
        query2.setParameter("gid", gid);
        List nameList = query2.list();
        String groupname = (String) nameList.get(0);
        Group newEntry = new Group();
        newEntry.setGid(gid);
        newEntry.setGroupname(groupname);
        newEntry.setUid(uid);
        currSession.saveOrUpdate(newEntry);
        return true;
    }

    @Override
    public boolean quitGroup(int gid, int uid){
        Session currSession = entityManager.unwrap(Session.class);
        //sanity check
        org.hibernate.Query query = currSession.createSQLQuery("select * from groupdb" +
                " where gid = :gid and uid = :uid and status = 'valid'").addEntity(Group.class);
        query.setParameter("gid", gid);
        query.setParameter("uid", uid);
        List list = query.list();
        if (list.isEmpty()) { // this user is already not in the group
            return false;
        }
        Group group = (Group) list.get(0);
        group.setStatus("invalid");
        return true;
    }

    @Override
    public boolean changeGroupname(int gid, String newGroupName){
        Session currSession = entityManager.unwrap(Session.class);
        //sanity check
        org.hibernate.Query query = currSession.createSQLQuery("select * from groupdb" +
                " where gid = :gid").addEntity(Group.class);
        query.setParameter("gid", gid);
        List list = query.list();
        if (list.isEmpty()) { // group not exist
            return false;
        }
        for (Object o : list) {
           Group group = (Group) o;
           group.setGroupname(newGroupName);
        }
        return true;
    }

    @Override
    public boolean sendFriendRequest(FriendApp friendApp) {
        Session currSession = entityManager.unwrap(Session.class);
        SQLQuery query = currSession.
                createSQLQuery("select * from friend_appdb where source = :source and destination = :destination").
                addEntity(FriendApp.class);
        query.setParameter("source", friendApp.getSource());
        query.setParameter("destination", friendApp.getDestination());
        List friend_app_list = query.list();
        if (friend_app_list.isEmpty()) {
            currSession.saveOrUpdate(friendApp);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @ResponseBody
    public Object getFriendRequest(int uid) {
        Session currSession = entityManager.unwrap(Session.class);
        SQLQuery query = currSession.
                createSQLQuery("select * from friend_appdb where source = :source and status = 'pending'").
                addEntity(FriendApp.class);
        query.setParameter("source", uid);
        List<FriendApp> friendAppList = new ArrayList<>();
        List list = query.list();
        for (Object o : list){
            FriendApp friendApp = (FriendApp) o;
            friendAppList.add(friendApp);
        }
        return friendAppList;
    }

    @Override
    public Object updateFriendApp(FriendApp friendApp) {
        Session currSession = entityManager.unwrap(Session.class);
        FriendApp originApp = currSession.get(FriendApp.class, friendApp.getAid());
        if (originApp.getStatus().equals("denied") || originApp.getStatus().equals("approved")) {
            return false;
        }
        if (originApp.getStatus().equals("pending")) {
           originApp.setStatus(friendApp.getStatus());
            if (originApp.getStatus().equals("denied")) {
                return "denied friend";
            }
            if (originApp.getStatus().equals("approved")) {
                SQLQuery query = currSession.
                        createSQLQuery("select * from frienddb where friend_id = :friend_id and uid = :uid").
                        addEntity(Friend.class);
                query.setParameter("uid", friendApp.getSource());
                query.setParameter("friend_id", friendApp.getDestination());
                List list = query.list();
                Friend newFriend = new Friend(originApp.getDestination(), originApp.getSource());
                Friend newFriend2 = new Friend(originApp.getSource(), originApp.getDestination());
                if (list.isEmpty()) {
                    currSession.saveOrUpdate(newFriend);
                    currSession.saveOrUpdate(newFriend2);
                    return true;
                } else {
                    newFriend.setStatus("valid");
                    return updateFriend(newFriend);
                }
            }
            currSession.saveOrUpdate(originApp);
        }
        return false;
    }

    @Override
    public Object updateFriend(Friend friend) {
        Session currSession = entityManager.unwrap(Session.class);
        SQLQuery query = currSession.
                createSQLQuery("select * from frienddb where friend_id = :friend_id and uid = :uid").
                addEntity(Friend.class);
        query.setParameter("uid", friend.getUid());
        query.setParameter("friend_id", friend.getFriend_id());
        List<Friend> friendList = new ArrayList<>();
        List list = query.list();
        for (Object o : list){
            Friend tmp = (Friend) o;
            friendList.add(tmp);
        }
        Friend originFriend = friendList.get(0);
        SQLQuery _query = currSession.
                createSQLQuery("select * from frienddb where friend_id = :friend_id and uid = :uid").
                addEntity(Friend.class);
        _query.setParameter("uid", friend.getFriend_id());
        _query.setParameter("friend_id", friend.getUid());
        List<Friend> _friendList = new ArrayList<>();
        List _list = _query.list();
        for (Object o : _list){
            Friend tmp = (Friend) o;
            _friendList.add(tmp);
        }
        Friend _originFriend = _friendList.get(0);
        if (friend.getStatus().equals("invalid") && originFriend.getStatus().equals("valid")) {
            originFriend.setStatus(friend.getStatus()); // delete friend
            _originFriend.setStatus(friend.getStatus());
            currSession.saveOrUpdate(originFriend);
            currSession.saveOrUpdate(_originFriend);
            return "successfully delete friend";
        }
        if (friend.getStatus().equals("valid") && originFriend.getStatus().equals("invalid")) {
            originFriend.setStatus(friend.getStatus()); // refriend
            _originFriend.setStatus(friend.getStatus());
            currSession.saveOrUpdate(originFriend);
            currSession.saveOrUpdate(_originFriend);
            return "successfully add old friend";
        }
        return "nothing changed";
    }

    @Override
    @ResponseBody
    public Object getFriend(int uid) {
        Session currSession = entityManager.unwrap(Session.class);
        SQLQuery query = currSession.
                createSQLQuery("select friend_id from frienddb where uid = :uid");
        query.setParameter("uid", uid);
        List list = query.list();
        List<User> friendList = new ArrayList<>();
        for (Object o : list){
            int friend_id = (Integer) o;
            friendList.add(currSession.get(User.class, friend_id));
        }
        User user = currSession.get(User.class, uid);
        return new FriendList(user, friendList);

    }
}
