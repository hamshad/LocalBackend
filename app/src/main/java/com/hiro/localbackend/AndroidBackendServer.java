package com.hiro.localbackend;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class AndroidBackendServer extends NanoHTTPD {

    private static final String TAG = "AndroidBackendServer";
    private final Context context;

    // In-memory database for demonstration
    private List<Map<String, Object>> items;

    public AndroidBackendServer(Context context, int port) {
        super(port);
        this.context = context;

        initializeData();
    }

    private void initializeData() {
        // Initialize our "database"
        items = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "Item 1");
        item1.put("description", "First sample item");

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("name", "Item 2");
        item2.put("description", "Second sample item");

        items.add(item1);
        items.add(item2);
    }


    @Override
    public Response serve(IHTTPSession session) {

        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request received: " + method + " " + uri);

        // Handle CORS (Cross-Origin Resource Sharing)
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");


        // Handle OPTIONS requests (preflight CORS requestsc)
        if (Method.OPTIONS.equals(method)) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", "");
        }

        try {
            // API Routes
            if (uri.equals("/api/items") && Method.GET.equals(method)) {
                return getItems(headers);
            } else if (uri.startsWith("/api/items/") && Method.GET.equals(method)) {
                int id = Integer.parseInt(uri.substring("/api/items/".length()));
                return getItem(id, headers);
            } else if (uri.equals("/api/items") && Method.POST.equals(method)) {
                Log.d(TAG, "serve: POST METHOD");
                return createItem(session, headers);
            } else if (uri.startsWith("/api/items/") && Method.PUT.equals(method)) {
                int id = Integer.parseInt(uri.substring("/api/items/".length()));
                return updateItem(id, session, headers);
            } else if (uri.startsWith("/api/items/") && Method.DELETE.equals(method)) {
                int id = Integer.parseInt(uri.substring("/api/items/".length()));
                return deleteItem(id, headers);
            }


            // Default response for unhandled routes
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    "{\"error\":\"Not Found\"}");

        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // GET /api/items - Get all items
    private Response getItems(Map<String, String> headers) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> item : items) {
                JSONObject jsonObject = new JSONObject();
                for (Map.Entry<String, Object> entry : item.entrySet()) {
                    jsonObject.put(entry.getKey(), entry.getValue());
                }
                jsonArray.put(jsonObject);
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString());
        } catch (JSONException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // GET /api/items/{id} - Get item by ID
    private Response getItem(int id, Map<String, String> headers) {
        for (Map<String, Object> item : items) {
            if ((int) item.get("id") == id) {
                try {
                    JSONObject jsonObject = new JSONObject();
                    for (Map.Entry<String, Object> entry : item.entrySet()) {
                        jsonObject.put(entry.getKey(), entry.getValue());
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", jsonObject.toString());
                } catch (JSONException e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                            "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                "{\"error\":\"Item not found\"}");
    }

    // POST /api/items - Create new item
    private Response createItem(IHTTPSession session, Map<String, String> headers) {
        try {
            // Parse the request body
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String body = bodyMap.get("postData");

            if (body == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Request body is required\"}");
            }

            JSONObject jsonRequest = new JSONObject(body);
            String name = jsonRequest.optString("name");
            String description = jsonRequest.optString("description");

            if (name.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Name is required\"}");
            }

            // Create new item
            int newId = items.size() + 1;
            Map<String, Object> newItem = new HashMap<>();
            newItem.put("id", newId);
            newItem.put("name", name);
            newItem.put("description", description);
            items.add(newItem);

            // Return created item
            JSONObject response = new JSONObject();
            response.put("id", newId);
            response.put("name", name);
            response.put("description", description);

            return newFixedLengthResponse(Response.Status.CREATED, "application/json", response.toString());
        } catch (IOException | JSONException | ResponseException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // PUT /api/items/{id} - Update item
    private Response updateItem(int id, IHTTPSession session, Map<String, String> headers) {
        try {
            // Find the item
            Map<String, Object> itemToUpdate = null;
            for (Map<String, Object> item : items) {
                if ((int) item.get("id") == id) {
                    itemToUpdate = item;
                    break;
                }
            }

            if (itemToUpdate == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                        "{\"error\":\"Item not found\"}");
            }

            // Parse request body
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String body = bodyMap.get("postData");

            if (body == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"Request body is required\"}");
            }

            JSONObject jsonRequest = new JSONObject(body);

            // Update the item
            if (jsonRequest.has("name")) {
                itemToUpdate.put("name", jsonRequest.getString("name"));
            }

            if (jsonRequest.has("description")) {
                itemToUpdate.put("description", jsonRequest.getString("description"));
            }

            // Return updated item
            JSONObject response = new JSONObject();
            for (Map.Entry<String, Object> entry : itemToUpdate.entrySet()) {
                response.put(entry.getKey(), entry.getValue());
            }

            return newFixedLengthResponse(Response.Status.OK, "application/json", response.toString());
        } catch (IOException | JSONException | ResponseException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // DELETE /api/items/{id} - Delete item
    private Response deleteItem(int id, Map<String, String> headers) {
        for (int i = 0; i < items.size(); i++) {
            if ((int) items.get(i).get("id") == id) {
                items.remove(i);
                JSONObject response = new JSONObject();
                try {
                    response.put("message", "Item deleted successfully");
                    return newFixedLengthResponse(Response.Status.OK, "application/json", response.toString());
                } catch (JSONException e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                            "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                "{\"error\":\"Item not found\"}");
    }
}
