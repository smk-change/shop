package com.smk.shop.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected final ObjectMapper mapper = new ObjectMapper();

    protected void sendJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(resp.getWriter(), data);
    }

    protected void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json;charset=UTF-8");
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        mapper.writeValue(resp.getWriter(), err);
    }

    protected void sendSuccess(HttpServletResponse resp, String message) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        Map<String, String> msg = new HashMap<>();
        msg.put("message", message);
        mapper.writeValue(resp.getWriter(), msg);
    }

    protected <T> T readJson(HttpServletRequest req, Class<T> clazz) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return mapper.readValue(sb.toString(), clazz);
    }
}
