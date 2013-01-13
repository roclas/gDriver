package com.roclas;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MiniServer {

  protected String start_and_get_code(int port) {
    ServerSocket s;
    String params="",code= "";
    System.out.println("Start on port "+port);
    try {
      s = new ServerSocket(port);
    } catch (Exception e) {
      System.out.println("Error: " + e);
      return null;
    }

    System.out.println("Waiting for connection");
    for (;;) {
      try {
        // wait for a connection
        Socket remote = s.accept();
        // remote is now the connected socket
        System.out.println("Connection, sending data.");
        BufferedReader in = new BufferedReader(new InputStreamReader( remote.getInputStream()));
        PrintWriter out = new PrintWriter(remote.getOutputStream());

        out.println("HTTP/1.0 200 OK");
        out.println("Content-Type: text/html");
        out.println("");
        
        int counter=0;
        try {
        	   //while (true) { str= in.readLine(); if(str.equals(""))counter++; 
        	   String str=".";
        	   while (!str.equals("")) { str= in.readLine();
        		   if (str.toLowerCase().trim().contains("code")){ params = str; break;}
        		   //out.println(str);
        		   //System.out.println(str);
        	   }
        } catch (IOException e) {
        	   System.err.println("Error: " + e);
        }
        // Send the HTML page
        String method = "post";
        out.print("<html>");
        //out.print("<form method="+method+">");
        //out.print("<textarea name=we></textarea></br>");
        //out.print("<input type=text name=a><input type=submit></form></html>");
        String[] getparams = params.split("&");
        for(String param:getparams){
        	String [] keyvalue=param.split("=");
        	if(keyvalue[0].toLowerCase().trim().contains("code")){ 
        		code=keyvalue[1].trim();
        	}
        }
        out.println("<br />code= "+code);
        out.println("<br /> Authentication Succesful; you can close this window");
        out.println("<script>setTimeout(function(){var ww = window.open(window.location, '_self'); ww.close();},3000)</script>");
        out.flush();
        remote.close();
        s.close();
        return code;
      } catch (Exception e) {
        System.out.println("Error: " + e);
      }
    }
  }

}