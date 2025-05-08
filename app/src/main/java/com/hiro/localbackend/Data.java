package com.hiro.localbackend;

import org.json.JSONException;
import org.json.JSONObject;

public record Data(int id, String name, String description) {

    // Constructor that takes a JSONObject
    public Data(JSONObject jsonObject) throws JSONException {
        this(
                jsonObject.getInt("id"),
                jsonObject.getString("name"),
                jsonObject.getString("description")
        );
    }

    // Method to convert Data to JSON string
    public String toJsonString() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("id", id);
            jsonObject.put("name", name);
            jsonObject.put("description", description);
            return jsonObject.toString();
        } catch (JSONException e) {
            e.fillInStackTrace();
            return "{}";
        }
    }
}
