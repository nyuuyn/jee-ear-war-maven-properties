package com.example.hello;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelloServletTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void doGet_setsContentTypeToHtml() throws Exception {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        new HelloServlet().doGet(request, response);

        verify(response).setContentType("text/html;charset=UTF-8");
    }

    @Test
    void doGet_returnsHtmlWithHelloWorldHeading() throws Exception {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        new HelloServlet().doGet(request, response);

        assertTrue(body.toString().contains("<h1>Hello World!</h1>"),
                "Response body should contain the H1 heading");
    }

    @Test
    void doGet_returnsWellFormedHtmlDocument() throws Exception {
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        new HelloServlet().doGet(request, response);

        String html = body.toString();
        assertTrue(html.contains("<!DOCTYPE html>"), "Should start with DOCTYPE declaration");
        assertTrue(html.contains("<html>"), "Should contain opening html tag");
        assertTrue(html.contains("</html>"), "Should contain closing html tag");
        assertTrue(html.contains("<title>Hello World</title>"), "Should contain page title");
    }
}