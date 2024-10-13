package com.cwuom.ououo;

import android.app.Application;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static String TAG = "ououo";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws ClassNotFoundException {
        if (!lpparam.packageName.equals("com.fenbi.android.leo")) return;
        Log.i(TAG, "Successfully hooked " + lpparam.processName);

        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = (Context) param.args[0];
                        ClassLoader classLoader = context.getClassLoader();
                        doHook(classLoader);
                    }
                });
    }

    public void doHook(ClassLoader classLoader){
        XposedHelpers.findAndHookMethod("android.webkit.WebView", classLoader, "loadUrl", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String url = (String) param.args[0];
                Log.d(TAG, url);

                if (url.startsWith("javascript:") && url.contains("dataDecrypt")) {
                    Pattern pattern = Pattern.compile("\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(url);

                    String base64EncodedString = "";
                    if (matcher.find()) {
                        base64EncodedString = matcher.group(1);
                        Log.d(TAG,"Extracted Base64 Encoded String: " + base64EncodedString);
                    } else {
                        Log.d(TAG, "No match found");
                    }

                    assert base64EncodedString != null;
                    if (base64EncodedString.length() > 100) {
                        Log.i(TAG, "Captured Base64: " + base64EncodedString);
                        String decryptedData = decryptBase64(base64EncodedString);
                        String modifiedUrl = url.replace(base64EncodedString, decryptedData);
                        param.args[0] = modifiedUrl;

                        Log.i(TAG, "Replaced URL: " + modifiedUrl);
                    }
                }
                super.beforeHookedMethod(param);
            }
        });

        XposedHelpers.findAndHookMethod("com.fenbi.android.leo.utils.r2", classLoader, "b", byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                byte[] bytes = (byte[]) param.args[0];
                String data = new String(bytes);
                JSONObject jsonObject = new JSONObject(data);
                jsonObject.put("costTime", 2000);
                String modifiedData = jsonObject.toString();
                byte[] modifiedBytes = modifiedData.getBytes();
                param.args[0] = modifiedBytes;
                Log.e(TAG, "report_data:" + modifiedData);
                super.beforeHookedMethod(param);
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
            }
        });
    }

    private String decryptBase64(String base64EncodedString) {
        try {
            byte[] decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT);
            Log.e(TAG, new String(decodedBytes));

            String regex = "\"result\":\"(.*?)\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(new String(decodedBytes));
            String result = "";
            if (matcher.find()) {
                result = matcher.group(1);
                Log.e(TAG,"result ->" + result);
            } else {
                Log.e(TAG,"result value was not found!!!");
            }

            String cleanedString = unicodeEscapeDecode(result.replace("\\n", ""));

            byte[] decodedBytes2 = java.util.Base64.getDecoder().decode(cleanedString);
            String data = getString(decodedBytes2);
            String resultMakeBase64 = java.util.Base64.getEncoder().encodeToString(data.getBytes("UTF-8"));
            String resultMakeUnicode173 = getString(resultMakeBase64);
            String resultMakeStr = java.util.Base64.getEncoder().encodeToString(resultMakeUnicode173.getBytes("UTF-8"));

            Log.e(TAG, data);
            return resultMakeStr;
        } catch (Exception e) {
            Log.i(TAG, "Error decoding Base64: " + e.getMessage());
            return "";
        }
    }

    private static String getString(byte[] decodedBytes2) throws JSONException {
        String data = new String(decodedBytes2, StandardCharsets.UTF_8);

        JSONObject json_data = new JSONObject(data);
        JSONObject examVO = json_data.getJSONObject("examVO");

        JSONArray questions = examVO.getJSONArray("questions");

        for (int i = 0; i < questions.length(); i++) {
            JSONObject question = questions.getJSONObject(i);
            JSONArray newAnswers = new JSONArray();
            newAnswers.put("1");
            question.put("answers", newAnswers);
        }

        data = json_data.toString();
        return data;
    }

    private static String getString(String resultMakeBase64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < resultMakeBase64.length(); i += 76) {
            int end = Math.min(i + 76, resultMakeBase64.length());
            sb.append(resultMakeBase64, i, end);
            sb.append("\\n");
        }
        String resultWithNewlines = sb.toString();

        String resultMakeUnicode = resultWithNewlines
                .replace("+", "\\u002b")
                .replace("=", "\\u003d");

        return "[null,{\"result\":\"" + resultMakeUnicode + "\"}]";
    }

    public static String unicodeEscapeDecode(String str) {
        StringBuilder sb = new StringBuilder();
        String[] parts = str.split("\\\\u");
        sb.append(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            String hex = parts[i];
            if (hex.length() >= 4) {
                String code = hex.substring(0, 4);
                char ch = (char) Integer.parseInt(code, 16);
                sb.append(ch);
                sb.append(hex.substring(4));
            } else {
                sb.append("\\u").append(hex);
            }
        }

        return sb.toString();
    }

}


