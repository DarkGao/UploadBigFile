package com.dark.controller;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PathVariable;

import com.dark.filter.MultiForm.ProgressInfo;

@Controller("uploadProgInfo")
@RequestMapping(value="/upload_prog")
public class UploadProgInfo {

	//取得根节点板块
	@RequestMapping(value="/{progId}",method=RequestMethod.GET)
	public @ResponseBody ProgressInfo getProgInfo(
			HttpServletRequest request,
			@PathVariable("progId") String progId)
			throws Exception{
		
		ServletContext context = request.getSession().getServletContext();    
		//WebApplicationContext ctx  = WebApplicationContextUtils.getWebApplicationContext(context); 
		ProgressInfo progInfo=ProgressInfo.getProgressInfo(context, progId);	
		//System.out.println("progId="+progId+"__"+progInfo);
		return progInfo;
		
		
	}
	
}
