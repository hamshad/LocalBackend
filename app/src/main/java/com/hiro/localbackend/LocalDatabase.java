package com.hiro.localbackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LocalDatabase {

    private static final String TAG = "LocalDatabase";
    private static final String DATA_KEY = "items_data";

    private Context context;
    private String app_name;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public LocalDatabase(Context context) {
        this.context = context;
        app_name = context.getPackageName();
        sharedPreferences = context.getSharedPreferences(app_name, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // Save list of Data to SharedPreferences
    public void saveData(List<Data> dataList) {
        JSONArray jsonArray = new JSONArray();

        for (Data data : dataList) {
            try {
                JSONObject jsonObject = new JSONObject(data.toJsonString());
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                Log.e(TAG, "Error converting data to JSON", e);
            }
        }

        editor.putString(DATA_KEY, jsonArray.toString());
        editor.apply();
    }

    // Get list of Data from SharedPreferences
    public List<Data> getData() {
        List<Data> dataList = new ArrayList<>();
        String jsonArrayString = sharedPreferences.getString(DATA_KEY, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonArrayString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Data data = new Data(jsonObject);
                dataList.add(data);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON data", e);
        }

        return dataList;
    }

    // Update a data item
    public boolean updateData(Data updatedData) {
        List<Data> dataList = getData();
        boolean found = false;

        List<Data> newDataList = new ArrayList<>();
        for (Data data : dataList) {
            if (data.id() == updatedData.id()) {
                newDataList.add(updatedData);
                found = true;
            } else {
                newDataList.add(data);
            }
        }

        if (found) {
            saveData(newDataList);
        }

        return found;
    }

    // Delete a data item
    public boolean deleteData(int id) {
        List<Data> dataList = getData();
        boolean found = false;

        List<Data> newDataList = new ArrayList<>();
        for (Data data : dataList) {
            if (data.id() != id) {
                newDataList.add(data);
            } else {
                found = true;
            }
        }

        if (found) {
            saveData(newDataList);
        }

        return found;
    }

    // Add a new data item
    public Data addData(String name, String description) {
        List<Data> dataList = getData();

        // Find the next ID
        int newId = 1;
        for (Data data : dataList) {
            if (data.id() >= newId) {
                newId = data.id() + 1;
            }
        }

        Data newData = new Data(newId, name, description);
        dataList.add(newData);
        saveData(dataList);

        return newData;
    }
}
