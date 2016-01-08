<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<%@ page import="java.util.*"%> 
<%@ page import="com.dark.filter.MultiForm"%> 
<%
	List<MultiForm.FormPart> listFormPart=(List<MultiForm.FormPart>)request.getAttribute(
			MultiForm.REQUEST_KEY_FORMPARTLIST);
	if(listFormPart!=null){
	    for(int i=0;i<listFormPart.size();i++){
	    	MultiForm.FormPart part=listFormPart.get(i);
			out.println("Form: name="+part.name+"; value="+part.value+
				"; fileName="+part.fileName+"; filePath="+part.newFileSavePath+
				"; fileSize="+part.getFileSize()+"<br>");
		}	
	}
%>
<html>
  <head><title>File Upload</title></head>
  <body>
  </body>
</html>