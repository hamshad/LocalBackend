package com.hiro.localbackend;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class AndroidBackendServer extends NanoHTTPD {

    private static final String TAG = "AndroidBackendServer";
    private final Context context;

    // LocalDatabase instance for data storage
    private LocalDatabase localDatabase;

    public AndroidBackendServer(Context context, int port) {
        super(port);
        this.context = context;
        this.localDatabase = new LocalDatabase(context);

        initializeData();
    }

    private void initializeData() {
        // Initialize our database with sample data if it's empty
        List<Data> items = localDatabase.getData();
        if (items.isEmpty()) {
            localDatabase.addData("Item 1", "First sample item");
            localDatabase.addData("Item 2", "Second sample item");
        }
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

        // Handle OPTIONS requests (preflight CORS requests)
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
            List<Data> items = localDatabase.getData();
            JSONArray jsonArray = new JSONArray();

            for (Data item : items) {
                jsonArray.put(new JSONObject(item.toJsonString()));
            }

            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString());
        } catch (JSONException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // GET /api/items/{id} - Get item by ID
    private Response getItem(int id, Map<String, String> headers) {
        List<Data> items = localDatabase.getData();

        for (Data item : items) {
            if (item.id() == id) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", item.toJsonString());
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

            // Create new item using LocalDatabase
            Data newData = localDatabase.addData(name, description);

            // Return created item
            return newFixedLengthResponse(Response.Status.CREATED, "application/json", newData.toJsonString());
        } catch (IOException | JSONException | ResponseException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // PUT /api/items/{id} - Update item
    private Response updateItem(int id, IHTTPSession session, Map<String, String> headers) {
        try {
            // Find the item
            List<Data> items = localDatabase.getData();
            Data itemToUpdate = null;

            for (Data item : items) {
                if (item.id() == id) {
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

            // Get values for update - use existing values as defaults
            String name = jsonRequest.has("name") ? jsonRequest.getString("name") : itemToUpdate.name();
            String description = jsonRequest.has("description") ?
                    jsonRequest.getString("description") : itemToUpdate.description();

            // Create updated data and update in database
            Data updatedData = new Data(id, name, description);
            localDatabase.updateData(updatedData);

            // Return updated item
            return newFixedLengthResponse(Response.Status.OK, "application/json", updatedData.toJsonString());
        } catch (IOException | JSONException | ResponseException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // DELETE /api/items/{id} - Delete item
    private Response deleteItem(int id, Map<String, String> headers) {
        boolean deleted = localDatabase.deleteData(id);

        if (deleted) {
            JSONObject response = new JSONObject();
            try {
                response.put("message", "Item deleted successfully");
                return newFixedLengthResponse(Response.Status.OK, "application/json", response.toString());
            } catch (JSONException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                        "{\"error\":\"" + e.getMessage() + "\"}");
            }
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    "{\"error\":\"Item not found\"}");
        }
    }
}
