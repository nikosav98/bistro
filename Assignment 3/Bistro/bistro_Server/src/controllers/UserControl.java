package controllers;


import java.sql.SQLException;

import database.SubscriberDAO;
import database.UserDAO;
import entities.Employee;
import entities.Subscriber;
import entities.User;
import responses.LoginResponse;
import requests.LoginRequest;

public class UserControl {

    private final UserDAO userDAO = new UserDAO();


    public LoginResponse login(LoginRequest req) throws SQLException  {
        User user = userDAO.getUserByUsernameAndPassword(req.getUsername(), req.getPassword());

        if (user == null) {
            return new LoginResponse(null, null, null, null); // fail
        }

        // אם אין EmployeeDB/SubscriberDB, אתה מחזיר רק userID+role
        return new LoginResponse(user.getUserID(), user.getRole(), null, null);
    }

}
