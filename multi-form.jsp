<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<%@ page import="java.util.*"%> 
         
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
  <head>
  <script language="javascript">
	var oXMLDoc=new ActiveXObject("Microsoft.XMLDOM");  
	oXMLDoc.async = true;  
	var theUniqueID= 0;
	var iTimerID=null;        //这个变量是作定时器的ID
	
	//让数据提交的同时执行显示进度条的函数
	function UploadData()    
	{
		theUniqueID = (new Date()).getTime() % 1000000000;
		// 清除原始数据
		PercentDone.style.width = "0%";
		ElapsedTime.innerHTML = "";
		TimeLeft.innerHTML = "";
		SizeCompleted.innerHTML = "";
		TotalSize.innerHTML = "";
		TransferRate.innerHTML = "";
	
		document.myform.action = "upload_result.jsp?progId="+ theUniqueID;  //处理上传数据的程序
		//将提交的数据放在一个名字是upload隐藏的iframe里面处理，这样提交的页面就不会跳转到处理数据的页
		document.myform.target="upload";  
		document.myform.submit();     //提交表单
	
		ProgressBar();      //开始执行反映上传情况的函数
	
	}
	
	function ProgressBar()
	{
		//获取上传状态数据的地址
	    sURL = "upload_prog/" + theUniqueID; //+ "&temp="+Math.random();
		//当响应Response状态改变时设置回调函数
	    oXMLDoc.onreadystatechange = Function( "fnLoadComplete();" );  
		//加载获取当前状态信息地址url
	    oXMLDoc.load( sURL );	
	}
	
	function fnLoadComplete()
	{
		//判断服务器响应状态，如果不是200 ok则退出本函数
	    var iReadyState;
	    try
	    {
	        iReadyState = oXMLDoc.readyState;
	    }
	    catch(e)
	    {
	        return;
	    }
	    if(  iReadyState != 4 ) return;
	    
	    if( oXMLDoc == null || oXMLDoc.xml == "" )
		{
			window.status = 'Xml load fault';
			return;
		}
	
		var oRoot = oXMLDoc.documentElement;     //获取返回xml数据的根节点
	
		if(oRoot != null)  
		{
		    if (oRoot.selectSingleNode("ErrInfo") == null)
			{
			    var readyState = oRoot.selectSingleNode("proState").text;
			    ProState.innerHTML = readyState;       //显示上传状态
			    //根据返回的数据在客户端显示
			    ElapsedTime.innerHTML = oRoot.selectSingleNode("elapseSecond").text;     //显示剩余时间
			    TimeLeft.innerHTML = oRoot.selectSingleNode("remainSecond").text;       //显示剩余时间
			    SizeCompleted.innerHTML = oRoot.selectSingleNode("curBytes").text;    //已上传数据大小
			    TotalSize.innerHTML = oRoot.selectSingleNode("totalBytes").text;    //总大小
			    TransferRate.innerHTML = oRoot.selectSingleNode("transRate").text; //传输速率
			    //ErrMsg.innerHTML = oRoot.selectSingleNode("errMsg").text; //传输速率
			    
				// 如果还没初始化完成，继续
			    if (readyState == "PROSTATE_NULL") {
			        bar1.style.display = 'block';  //让显示上传进度显示的层的可见
			        PercentDone.style.width = "0%";        //设置进度条的百分比例			
			        iTimerID = setTimeout("ProgressBar()", 100);
			    }
			    // 上传进行中
			    else if (readyState == "PROSTATE_LOADING")              //文件上传结束就取消定时器
			    {
	
			        bar1.style.display = 'block';  //让显示上传进度显示的层的可见
			        PercentDone.style.width = (oRoot.selectSingleNode("transPercent").text * 0.8 + 10) + "%";        //设置进度条的百分比例
			        //根据返回的数据在客户端显示
			        ElapsedTime.innerHTML = oRoot.selectSingleNode("elapseSecond").text;     //显示剩余时间
			        TimeLeft.innerHTML = oRoot.selectSingleNode("remainSecond").text;       //显示剩余时间
			        SizeCompleted.innerHTML = oRoot.selectSingleNode("curBytes").text;    //已上传数据大小
			        TotalSize.innerHTML = oRoot.selectSingleNode("totalBytes").text;    //总大小
			        TransferRate.innerHTML = oRoot.selectSingleNode("transRate").text; //传输速率
			        //ErrMsg.innerHTML = oRoot.selectSingleNode("ErrMsg").text; //传输速率
	
			        //这里设定时间间隔是0.5秒，你也可以根据你的情况修改获取数据时间间隔
			        iTimerID = setTimeout("ProgressBar()", 50);
			    }
			    else if (readyState == "PROSTATE_PARSE")              //文件上传结束就取消定时器
			    {
			        PercentDone.style.width = "90%";
			        SizeCompleted.innerHTML = oRoot.selectSingleNode("curBytes").text;    //已上传数据大小		        
			        iTimerID = setTimeout("ProgressBar()", 100);
			        //alert("上传数据结束，服务器处理中...");
			    }
				// 上传结束
	            else if (readyState == "PROSTATE_END")
				{
					PercentDone.style.width = "100%";        //设置进度条的百分比例
					if (iTimerID != null)
						clearTimeout(iTimerID)
					iTimerID = null;	
					alert("上传结束");
				}
				// 上传结束
				else
				{
					PercentDone.style.width = "100%";        //设置进度条的百分比例
					if (iTimerID != null)
						clearTimeout(iTimerID)
					iTimerID = null;	
					alert("上传结束");
				}
			}
			else
			{
			    //alert(oRoot.selectSingleNode("ErrInfo").text);
				upload.location.href = "about:blank";
			}
		}
	
	}
	
	function CacelUpload()
	{
		upload.location.href = "about:blank";
		if (iTimerID != null)
			clearTimeout(iTimerID)
		iTimerID = null;	
		bar1.style.display = '';
	}

</script>
  
  <title>File Upload</title></head>
  <body>

<base target="_blank">
<form name="myform" method="post" action="upload_result.jsp" enctype="multipart/form-data" target="upload">
      <p>姓名：<input type=text name="username" ></p>
      <p>昵称：<input type=text name="nickname" ></p>
      <p>备注：<textarea name="remark"></textarea></p>
      <p>文件1：<input type=file name="file1"  ></p>
      <p>文件2：<input type=file name="file2" ></p>
      
	<input type="button" value="上传" onclick="UploadData()">
	<input type="button" value="取消上传" onclick="CacelUpload()"><br>
	<div id=bar1 style="display:">
		<table border="0" width="100%">
		  <tr>
			<td><b>传送:</b></td>
		  </tr>
		  <tr bgcolor="#999999">
			<td>
			  <table border="0" width="" cellspacing="1" bgcolor="#0033FF" id="PercentDone">
				<tr>
				  <td>&nbsp;</td>
				</tr>
			  </table>
			</td>
		  </tr>
		  <tr>
			<td>
				<table border="0" cellpadding="0" cellspacing="0">
				    <tr><td>上传状态:&nbsp</td><td id="ProState" /></tr>
					<tr><td>总 大 小:&nbsp</td><td id="TotalSize" /><td>(字节)</td></tr>
					<tr><td>已经上传:&nbsp</td><td id="SizeCompleted" /><td>(字节)</td></tr>
					<tr><td>平均速率:</td><td id="TransferRate" /><td>(字节/秒)</td></tr>
					<tr><td>使用时间:</td><td id="ElapsedTime" /><td>(秒)</td></tr>
					<tr><td>剩余时间:</td><td id="TimeLeft" /><td>(秒)</td></tr>
					<!-- <tr><td>错误信息:</td><td id="ErrMsg"></td></tr> -->
				</table>
			</td>
		  </tr>
		</table>
	</div>
	<iframe name="upload" style="width:100%"></iframe>     
         
  </form>

  </body>
</html>