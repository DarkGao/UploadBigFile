package com.dark.filter;
/**
 * 作者：DarkGao
 * 首次开发：2015/3/10
 * 
 */

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.xml.bind.annotation.XmlRootElement; 
import javax.xml.bind.annotation.XmlElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Date;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;

import com.dark.util.ArrayUtils;
import com.dark.util.ByteUtils;
import com.dark.util.GlobInfo;

public class MultiForm implements Filter {
	
	//定义http协议中用到的一些关键字常量
	private final static String SIGN_BOUNDARY = "boundary=";
	private final static String SIGN_FORMELEMENT = "name=";
	private final static String SIGN_FILENAME = "filename=";
	private final static String SIGN_MULTIDATA = "multipart/form-data";
	private final static String SIGN_CONTENTDISPOS ="Content-Disposition:";
	private final static String SIGN_CONTENTTYPE = "Content-Type:";
	private final static String METHOD_POST="post";
    private final static Long 	N_MAX_CONTENTLEN=1024*1024*1024L;	//最大为1G接收请求内容
    private final static Integer N_CHUNKBYTES=64*1024;	//一次读取64K大小
    //向后续页面传递的两个Request范围key
    public final static String REQUEST_KEY_FORMPARTLIST="FormPartList";
    public final static String REQUEST_KEY_PROGERR="ProgErr";
    private final static String APP_KEY_APPNAME = "DarkUploadApp";
    private final static String DEF_SAVEPATH="uploadFile";
    //定义xml配置filter时加载的属性
    private final static String CONFIG_AUTODELPROG="autoDelProg";
    private final static String CONFIG_MAXPKGBYTES="maxPkgBytes";
    private final static String CONFIG_RECVEXTLIST="recvExtList";
    private final static String CONFIG_REJECTEXTLIST="rejectExtList";
    private final static String CONFIG_FILESAVEPATH="fileSavePath";
    private final static String CONFIG_USEORIFILENAME="useOriFileName";

    //HTTP MULTPART段分界线
    private String boundary = "";
    private FilterConfig config=null;
    //文件输出流对象，为了写文件时只打开一次文件
    private FileOutputStream fos=null;
    //private FileOutputStream fosDebug=null;
    //配置文件参数，从.xml中读取
    private ConfigParam configParam=null;
    private ProgressInfo.ProErrEnum progErr=ProgressInfo.ProErrEnum.PROERR_OK;
    
    /**
     * 进度信息类,描述了一次上传请求的进度信息
     * 该对象会用来被客户端轮询请求以获得当前传输大文件过程中的进度信息
     * @author DarkGao
     *
     */
    @XmlRootElement
    public static class ProgressInfo{
    	//当前进度状态
        public enum ProStateEnum{
            PROSTATE_NULL,      //无状态
            PROSTATE_LOADING,   //上传数据
            PROSTATE_LOADED,    //上传数据完毕
            PROSTATE_PARSE,     //数据分析中
            PROSTATE_END;        //全部完毕
        }

        //上传进度错误
        public enum ProErrEnum{
            PROERR_OK,           //无错误
            PROEFF_CFGFILE,      //配置文件读取错误
            PROERR_PARAM,        //参数错误
            PROERR_REQ_SYS,      //处理请求时的系统错误,比如取得当前请求包内容异常
            PROERR_REQ_RULE,     //请求包规则错误
            PROERR_REQ_SIZE,     //请求包过大
            PROERR_IO_FILE;       //保存文件错误,包括无法创建指定路径，指定文件，或无法打开指定文件
        }
        //当前接收字节数
	    private long curBytes=-1l;
	    //整个http请求的总共字节数
	    private long totalBytes=-1l;
	    //请求发起起始时间
	    private Date startTime=new Date();
	    //当前进度时刻时间
	    private Date curTime=new Date();
	    //当前进度状态
	    private ProStateEnum proState=ProStateEnum.PROSTATE_NULL;

	    @Override
	    public String toString(){
	    	String result="";
	    	result=this.getClass()+":curBytes="+curBytes+";"+
	    			"totalBytes="+totalBytes+";"+
	    			"startTime="+startTime+";"+
	    			"curTime="+curTime+";"+
	    			"proState="+proState;
	    	return result;
	    }
	    
	    //取得当前进度状态
	    @XmlElement
	    public ProStateEnum getProState(){
	    	return proState;
	    }
	    
	    //取得当前接收字节数
	    @XmlElement
	    public Long getCurBytes(){
	    	return curBytes;
	    }
	    
	    //取得本次HTTP包全部字节数
	    @XmlElement
	    public Long getTotalBytes(){
	    	return totalBytes;
	    }
	    
        //当前时刻已经花费的时间(秒数)
	    @XmlElement
        public Long getElapseSecond(){
            Long nReturn=0l;
            nReturn=(curTime.getTime()-startTime.getTime())/1000;
            return nReturn;
        }

        //当前上传速度(字节/秒)
	    @XmlElement
        public long getTransRate(){
        	long nReturn=0l;
            //获得花费的时间(秒)
        	long nSeconds=getElapseSecond();
            //计算当前上传速度,字节/秒
            if(nSeconds>0)
                nReturn=curBytes/nSeconds;
            return nReturn;
        }

        //当前进度百分比(0--100之间的数)
	    @XmlElement
        public int getTransPercent(){
            int nReturn=0;
            if(totalBytes>0)
                nReturn=(int)(curBytes*100/totalBytes);
            return nReturn;
        }

        //当前剩余秒数(小于零为未知时间)
	    @XmlElement
        public int getRemainSecond(){
            int nReturn=0;
            long nRate=getTransRate();
            if(nRate>0)
                nReturn=(int)((totalBytes-curBytes)/nRate);
            return nReturn;
        }

        //根据progId取得在context中的进度对象
	    //注意，进度对象都被存储在context中的Map中(Application级别的内存共享区)
        @SuppressWarnings("unchecked")
    	public static ProgressInfo getProgressInfo(ServletContext context,String progId){
            if (progId == null)
                return null;
            progId = progId.trim();
            ProgressInfo objInfo = null;
            if (progId.isEmpty())
                return objInfo;
            objInfo = new ProgressInfo();
            
            HashMap<String,ProgressInfo> progMap=(HashMap<String,ProgressInfo>)
            		context.getAttribute(APP_KEY_APPNAME);
            if (progMap != null){
                ProgressInfo objTemp= (ProgressInfo)progMap.get(progId);
                if (objTemp != null){
                    objInfo.curBytes = objTemp.curBytes;
                    objInfo.curTime = objTemp.curTime;
                    objInfo.proState = objTemp.proState;
                    objInfo.startTime = objTemp.startTime;
                    objInfo.totalBytes = objTemp.totalBytes;
                }
            }
            return objInfo;
        }    
        
        //从context的map中删除指定progId的进度对象
        @SuppressWarnings("unchecked")
		public static void delProgressInfo(ServletContext context,String progId){
        	progId = progId.trim();
            if (progId.isEmpty())
                return;
            //如果当前上下文中Application为空，那么创建ProgressInfo字典进入Application
        	HashMap<String,ProgressInfo> progMap = (HashMap<String,ProgressInfo>)(
        			context.getAttribute(APP_KEY_APPNAME));
        	if(progMap==null)
        		return;
        	//对全局context中对象修改需要进行线程锁控制
            synchronized(progMap){
            	ProgressInfo objTemp= (ProgressInfo)progMap.get(progId);
            	progMap.remove(objTemp);
            	if(progMap.size()<=0)
            		context.removeAttribute(APP_KEY_APPNAME);
            }// end synchronized
        }        
        
        //将当前的ProgressInfo对象加入context(Application共享内存区)
        @SuppressWarnings("unchecked")
		private static void updateProgressInfo(ServletContext context,
            String progId,ProgressInfo objProgress){ 
        	progId = progId.trim();
            if (progId.isEmpty())
                return;
            //如果当前上下文中Application为空，那么创建ProgressInfo字典进入Application
        	HashMap<String,ProgressInfo> progMap = (HashMap<String,ProgressInfo>)(
        			context.getAttribute(APP_KEY_APPNAME));
        	//对全局context中对象修改需要进行线程锁控制
            if (progMap == null)
            {
            	progMap = new HashMap<String,ProgressInfo>(31);
            	context.setAttribute(APP_KEY_APPNAME, progMap);
            }
            //由于针对Application操作是多线程并发，所以需要加同步锁控制
            synchronized(progMap){	            
	            //从字典中寻找当前ID的ProgressInfo
	            ProgressInfo objCur = (ProgressInfo)progMap.get(progId);
	            //如果没找到则将当前ProgressInfo加入其中
	            if (objCur == null){
	                objCur = new ProgressInfo();
	                progMap.put(progId, objCur);
	            }
	            objCur.curBytes = objProgress.curBytes;
	            objCur.curTime = objProgress.curTime;
	            objCur.proState = objProgress.proState;
	            objCur.startTime = objProgress.startTime;
	            objCur.totalBytes = objProgress.totalBytes;
            }
        }        
    }//End ProgressInfo
    
    
    /**
     * Http Form 表单数据结构
     * @author DarkGao
     *
     */
    public static class FormPart implements Cloneable{
    	public String name="";
    	public String value="";
    	public String fileName="";
    	public String fileExt="";
    	public String contentType="";
    	public String fileSavePath="";
    	public String newFileName="";
    	public String newFileSavePath="";
    	public boolean isValid=true;
    	public FileErrEnum fileErr=FileErrEnum.FILEERR_OK;
    	public enum FileErrEnum{
    		FILEERR_OK,				//保存文件成功
    		FILEERR_FAIL_CREATE,	//创建文件失败
    		FILEERR_FAIL_EXT		//扩展名不符合要求
    	}
    	@Override
    	public String toString(){
    		String result="";
    		result=this.getClass()+":name="+name+";"+
    				"value="+value+";"+
    				"fileName="+fileName+";"+
    				"fileExt="+fileExt+";"+
    				"contentType="+contentType+";"+
    				"fileSavePath="+fileSavePath+";"+
    				"newFileName="+newFileName+";"+
    				"newFileSavePath="+newFileSavePath+";"+
    				"isValid="+isValid+";"+
    				"fileErr="+fileErr;
    		return result;
    	}
    	//上传表单是否为文件类型
    	public Boolean isFileInput(){
    		return !contentType.isEmpty();
    	}
    	//取得上传后保存文件的大小
    	public Long getFileSize(){
    		if(!newFileSavePath.isEmpty()){
    			return FileUtils.sizeOf(new File(newFileSavePath));
    		}
    		return 0l;
    	} 
    	//克隆
    	public Object clone(){
    		FormPart o=null;
    		try{
    			o=(FormPart)super.clone();
    		}catch(CloneNotSupportedException e){
    			e.printStackTrace();
    		}
    		return o;
    	}
    }//End FormPart
    
    /**
     * xml中filter配置信息
     * 这些配置信息主要用来限制接收文件条件，比如接收的文件大小限制，扩展名限制等
     * @author DarkGao
     *
     */
    private class ConfigParam{
    	//最大请求包尺寸
    	public int maxPkgBytes = 0;
        //保存文件路径
    	public String fileSavePath = "";
        //只接收某些扩展名列表,用','分割,例如：'jpg,txt'
    	public String recvExtList = "";
        //拒绝某些扩展名列表,用','分割,例如：'ext,com,bat'
    	public String rejectExtList = "";
        //保存文件时是否使用原文件名保存
    	public boolean useOriFileName = true;
        //是否在本次请求结束后自动删除该进度信息
    	public boolean autoDelProg = true;
    	
    	public String toString(){
    		String result="";
    		result=this.getClass()+":maxPkgBytes="+maxPkgBytes+";"+
    				"fileSavePath="+fileSavePath+";"+
    				"recvExtList="+recvExtList+";"+
    				"rejectExtList="+rejectExtList+";"+
    				"useOriFileName="+useOriFileName+";"+
    				"autoDelProg="+autoDelProg+";";
    		return result;
    	}
    }//End ConfigParam    
    	
    
    /**
     * 将不完整的文件件片段信息写进原文件内
     * 这里的文件句柄已经在之前被开启(文件已经被创建)
     * @param buf
     * @param lastForm
     * @return
     */
    private Boolean appendFileSect(byte[] buf,FormPart lastForm){
    	Boolean result=false;
    	if(buf.length>0 && !lastForm.newFileSavePath.isEmpty())
    	{
    		try{
    			//将本次接收的内存区写入文件
    			if(fos!=null)
    				fos.write(buf);
    			result=true;
    		}catch(IOException e) {
    			e.printStackTrace();
    		}//end try
    	}	   
    	return result;
    }
	
	/**
	 * 从局部内存区中找到Boundary段信息
	 * 如果从本次接收的局部内存中找到了整体的表单信息，则存入表单信息结构中
	 * 这里分为几种情况
	 * 1.本次内存区中包含了1个或多个完整的表单信息。可能还会有剩余，即不完整的表单信息
	 * 2.本次内存区中只包含了不完整的表单信息，比如大文件中间的某段片段信息
	 * 
	 * @param buf
	 * @param scanStart
	 * @param boundary
	 * @param lastForm
	 * @param curForm
	 * @return
	 */
	private int getBoundarySectFromBuf(byte[] buf,int scanStart,String boundary,
			   FormPart lastForm,FormPart curForm){
		curForm.isValid=true;
		int scanEnd=-1;
		byte[] scanKey=ByteUtils.getBytes(boundary);
		if(buf.length<=0 || scanKey.length<=0){
			curForm.isValid=false;
			return scanEnd;
		}
		//在内存区中scanStart开始位置查找boundary key
		int start=ArrayUtils.indexOf(buf, scanKey, scanStart);
		//如果没找到boundary，但buf中的确有内容，则为上一个FormPart中文件的片段信息
		if(start<0)
		{   
			//这里需要进行写操作
			if(lastForm==null || lastForm.newFileSavePath.isEmpty())
				return scanEnd;
			scanEnd=appendFileSect(buf,lastForm)?buf.length:scanEnd;
			curForm.isValid=false;
			return scanEnd;
		}
		//如果找到了boundary但并不是起点位置则
		else if((start-scanStart)!=2)
		{
			//则将boundary前面的部分写入上一个FormPart文件中
			byte[] tempBuf=ArrayUtils.copyOfRange(buf, 0, start-2);
			if(lastForm==null || lastForm.newFileSavePath.isEmpty())
				return scanEnd;		   
			appendFileSect(tempBuf,lastForm);
			try{
				if(fos!=null)
					fos.close();
				fos=null;
			}catch(IOException e){
				e.printStackTrace();
			}
			tempBuf=null;			
		}
		
		//对当前的form结构进行初始化
		curForm.name="";
		curForm.value="";
		curForm.fileName="";
		curForm.fileExt="";
		curForm.contentType="";
		curForm.newFileSavePath="";	   
		
		//如果找到指定第一个boundary，则查找下一行"Content-Disposition:"
		start=ArrayUtils.indexOf(buf, SIGN_CONTENTDISPOS.getBytes(),start);
		if(start<0){
			curForm.isValid=false;		
			return scanEnd;
		}
		//取得"Content-Disposition:"的一行
		ArrayUtils.LineReturn line=ArrayUtils.getLine(buf, start);//ByteUtils.getString(byteLine);
		if(line.line.length()<=0){
			curForm.isValid=false;					
			return scanEnd;
		}
		//取得"name="
		start=line.line.indexOf(SIGN_FORMELEMENT);
		if(start<0){
			curForm.isValid=false;
			return scanEnd;
		}
		String formName=line.line.substring(start+SIGN_FORMELEMENT.length());
		int end=formName.indexOf(";");
		if(end>0)
			formName=formName.substring(0, end);
		//去掉formName的'\"'
		formName=formName.replace("\"", "");
		//查找"filename="
		start=line.line.indexOf(SIGN_FILENAME);
		//如果没找到filename=,则说明为form普通字段，否则为文件二进制字段
		if(start<0){
			//跳过一个"\r\n"
			//继续搜索下一个"\r\n"
			line=ArrayUtils.getLine(buf, line.endIndex);
			line=ArrayUtils.getLine(buf, line.endIndex);
			curForm.name=formName;
			curForm.value=line.line;
			scanEnd=line.endIndex;
		}
		//如果找到filename=
		else{
			curForm.name=formName;
			curForm.value="";	
			//在内存区中找到下一个boundary key
			end=ArrayUtils.indexOf(buf, scanKey, line.endIndex);
			//如果找到第二个boundary则
			if(end>0){
				scanEnd=end-2;
			}
			else{
				scanEnd=buf.length;
			}
		   
			//截取filename=
			String fileName=line.line.substring(start+SIGN_FILENAME.length());
			fileName=fileName.replace("\"","");
			//**?转换中文编码问题
			System.out.println("fileName="+fileName);
			String arr[]=fileName.split("\\.");
			if(arr.length>1){
				curForm.fileName=arr[0];
				curForm.fileExt=arr[1];
			}	   
			//查找下一行
			line=ArrayUtils.getLine(buf, line.endIndex);
			start=line.line.indexOf(SIGN_CONTENTTYPE);
			//找到Content-Type:内容
			if(!line.line.isEmpty() && start>=0){
				arr=line.line.split(":");
				if(arr.length==2){
					curForm.contentType=arr[1].trim();
				}
				//找到下一空行
				line=ArrayUtils.getLine(buf, line.endIndex);
			   
				//此处为二进制信息开始地点,开始进行写文件操作
				String newFileName=curForm.newFileName.isEmpty()?curForm.fileName:curForm.newFileName;
				if(!newFileName.isEmpty()){
					String filePath=curForm.fileSavePath+newFileName;
					if(!curForm.fileExt.isEmpty())
						filePath+="."+curForm.fileExt;
					curForm.newFileSavePath=filePath;
					byte[] tempBuf=ArrayUtils.copyOfRange(buf, line.endIndex, scanEnd);
					try{
						//将本次接收的内存区写入文件
						if(fos!=null)
							fos.close();
						fos=FileUtils.openOutputStream(new File(filePath),false);
						fos.write(tempBuf);
						//fos.close();
					}catch(IOException e) {
						e.printStackTrace();
						fos=null;
						scanEnd=-1;
					}//end try
					tempBuf=null;
				}
			}//end if
		}
		return scanEnd;
	}
	
	/**
	 * 从输入的流中分段读取用户请求，边接收边进行分段解析，通过累计后，最后形成表单列表
	 * 
	 * @param is
	 * @param contentLen
	 * @param boundary
	 * @param inputList
	 * @param progInfo
	 * @param context
	 * @param progId
	 * @return
	 */
	private Boolean parseInputStream(InputStream is,Integer contentLen,
			   String boundary,List<FormPart> inputList,
			   ProgressInfo progInfo,ServletContext context,String progId) {
		Boolean result=false;
		if(is==null || contentLen<=0 || 
				boundary==null || boundary.length()<=0)
			return result;

		//更新进度信息为正在上传状态
        progInfo.curTime = new Date();
        progInfo.proState = ProgressInfo.ProStateEnum.PROSTATE_LOADING;
        ProgressInfo.updateProgressInfo(context, progId, progInfo);
        //System.out.println(progInfo);
        
		try {
		   //FileOutputStream fos=FileUtils.openOutputStream(new File("D:\\fileTemp2.dat"),true);      	   
		   //从输入流中循环读取客户发上来的请求    	   
		   //循环读取字节流，一次读取64K数据
			int totalLen=0;
			FormPart lastForm=new FormPart();
			FormPart curForm=new FormPart();
			curForm.fileSavePath=this.configParam.fileSavePath;
			//System.out.println("fileSavePath="+curForm.fileSavePath);
					
		   	while (totalLen<contentLen) {
		   		//创建最大读取缓冲区
		   		byte[] bufTemp=new byte[N_CHUNKBYTES];        	   
		   		int remainLen=contentLen-totalLen;
		   		//每次最多读取N_CHUNKBYTES字节
		   		int chunkSize=is.read(bufTemp,0,(remainLen>N_CHUNKBYTES)?N_CHUNKBYTES:remainLen);
		   		if(chunkSize<=0)
		   			break;
		   		//fosDebug.write(bufTemp);
                
		   		byte[] buf=ArrayUtils.copyOfRange(bufTemp, 0, chunkSize);
		   		bufTemp=null;
		   		totalLen+=chunkSize;
		   		
		   		//更新进度信息为当前传输过程中进度状态
		   		progInfo.curTime = new Date();
		   		progInfo.curBytes = totalLen;
		   		ProgressInfo.updateProgressInfo(context, progId, progInfo);
		   		//System.out.println(progInfo);
                
		   		//从当前接收的缓冲区中动态解析HTML multipart信息
		   		//这里使用到循环，主要是为了处理本次接收的最大N_CHUNKBYTES字节数据中将包含多段FormPart信息
		   		//如果包含多段FormPart，我们需要使用循环来分解并建立对象
		   		int bufIndex=0;
		   		while(bufIndex<buf.length){
		   			bufIndex=getBoundarySectFromBuf(buf,bufIndex,boundary,lastForm,curForm);
		   			if(bufIndex<0)
		   				break;
		   			lastForm=curForm;
		   			//判断本次Form Part信息是否完整，如果完整则填入结果列表
		   			if(curForm.isValid){
		   				FormPart tempForm=(FormPart)curForm.clone();
		   				inputList.add(tempForm);
		   			}
		        }
		   		if(bufIndex>=0){
		   			result=true;
		   		}
		        buf=null;
		    }
		   	
		    //fos.close();
		} catch (FileNotFoundException e) {
			//更新进度信息
		    e.printStackTrace();
		} catch (IOException e) {
			//更新进度信息
		    e.printStackTrace();
		}
		//更新进度信息对象为结束状态
		progInfo.proState = ProgressInfo.ProStateEnum.PROSTATE_END;
		ProgressInfo.updateProgressInfo(context, progId, progInfo);
		System.out.println(progInfo);
	    return result;
	}
	
	/**
	 * 获取本次上传对应的表单元素间的分隔符，注意该分隔符是随机生成的
	 * @param contentType
	 * @return
	 */
    private String getBoundary(String contentType) {
        String tempStr = "";
        if (contentType != null && contentType.startsWith(SIGN_MULTIDATA)
                && contentType.indexOf(SIGN_BOUNDARY) != -1) {
            //获取表单每个元素的分隔符
            tempStr = contentType
                    .substring(
                            contentType.indexOf(SIGN_BOUNDARY)
                                    + SIGN_BOUNDARY.length()).trim();
        }
        return tempStr;
    }
    
    /**
     * 获得xml文件中配置filter的配置信息
     * @param config
     * @param configParam
     */
    private void getConfigParam(FilterConfig config,ConfigParam configParam){
    	if(config==null || configParam==null)
    		return;
    	configParam.autoDelProg=Boolean.parseBoolean(config.getInitParameter(CONFIG_AUTODELPROG));
    	configParam.maxPkgBytes=Integer.parseInt(config.getInitParameter(CONFIG_MAXPKGBYTES));
    	configParam.recvExtList=config.getInitParameter(CONFIG_RECVEXTLIST);
    	configParam.rejectExtList=config.getInitParameter(CONFIG_REJECTEXTLIST);
    	configParam.fileSavePath=config.getInitParameter(CONFIG_FILESAVEPATH);
    	configParam.useOriFileName=Boolean.parseBoolean(config.getInitParameter(CONFIG_USEORIFILENAME));
    	System.out.println(configParam);
    }
    
    /**
     * 检查所有请求表单项中的文件扩展名是否符合xml中配置选项
     * @param listFormPart
     */
    private void checkFileExt(List<FormPart> listFormPart){
        //检查用户上传的文件扩展名
        String[] recvExtArr=null;
        if(!configParam.recvExtList.isEmpty())
        	recvExtArr=configParam.recvExtList.split(",");
        String[] rejectExtArr=null;
        if(!configParam.rejectExtList.isEmpty())
        	rejectExtArr=configParam.rejectExtList.split(",");
        
        Boolean recvAll=true;
        
        //寻找"*"
        for(int i=0;i<recvExtArr.length;i++){
        	if(recvExtArr[i].equalsIgnoreCase("*")){
        		recvAll=false;
        		break;
        	}
        }
        
        for(int i=0;i<listFormPart.size();i++){
        	FormPart part=listFormPart.get(i);
        	Boolean isDel=false;
	        //如果接受所有扩展名,则需要检查rejectExtArr中内容
	        if(recvAll){
	        	isDel=false;
	        	if(rejectExtArr!=null){
		        	for(int j=0;j<rejectExtArr.length;j++){
		        		if(part.fileExt.equalsIgnoreCase(rejectExtArr[j])){
		        			isDel=true;
		        			break;
		        		}
		        	}
	        	}
	        }
	        //否则只检查recvExtArr内容
	        else if(recvExtArr.length>0){
	        	isDel=true;
	        	if(recvExtArr!=null){
		        	for(int j=0;j<recvExtArr.length;j++){
		        		if(part.fileExt.equalsIgnoreCase(recvExtArr[j])){
		        			isDel=false;
		        			break;
		        		}
		        	}
	        	}
	        }
	        if(isDel){
    			//删除该文件并标记该文件不符合要求
    			File file=new File(part.newFileSavePath);
    			file.delete();		
    			part.fileErr=FormPart.FileErrEnum.FILEERR_FAIL_EXT;
	        }
	        System.out.println(part);
        }    	
    }

	/**
	 * 初始化Filter	
	 * 主要完成了加载xml文件中的filter配置信息
	 * 以及根据xml文件中的文件保存路径以及本app的系统目录创建最终的文件保存路径
	 */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    	this.config=filterConfig;
    	this.configParam=new ConfigParam();
    	//加载来自XML的SAVEPATH
    	//this.savePath=config.getInitParameter("savePath");
    	getConfigParam(this.config,this.configParam);
    	System.out.println(configParam);
    	String savePath=this.configParam.fileSavePath;
    	if(savePath==null || savePath.isEmpty())
    		savePath=DEF_SAVEPATH;
    	//取得系统当前目录
    	String appPath=GlobInfo.getAppPath(this);
    	appPath=appPath.replace('/', '\\');
    	savePath=appPath+savePath;
    	if(savePath.charAt(savePath.length()-1)!='\\' || 
    			savePath.charAt(savePath.length()-1)!='/')
    		savePath+='\\';
    	this.configParam.fileSavePath=savePath;
    	System.out.println("savePath="+this.configParam.fileSavePath);    	
    }

    
	/*fosDebug=FileUtils.openOutputStream(new File("D:\\MultiData.txt"),true);		
    Enumeration<?> headerNames = request.getHeaderNames();
    while(headerNames.hasMoreElements()) {            
    	String headerName = (String)headerNames.nextElement();
        // 同一个请求头名可能出现多次            
    	Enumeration<?> values = request.getHeaders(headerName);            
    	while(values.hasMoreElements()) {                
    		//out.println(headerName + ":" + (String)values.nextElement() + "<br>");  
    		String strLine=headerName + ":" + (String)values.nextElement()+"\r\n";
    		fosDebug.write(strLine.getBytes());
    	}        
    }        
    String strLine="\r\n\r\n";
    fosDebug.write(strLine.getBytes());*/    
    
    /**
     * 过滤器主函数
     * 该函数通过输入字节流调用相关的分析函数最终完成对本次http multipart form 的request请求 
     * 将文件表单项写到指定位置，将其他普通表单项形成数据结构供后续页面调用
     * 
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, 
    		FilterChain filterChain) throws IOException, ServletException {
    	
    	//主输入对象和主输出对象
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        ServletContext context=this.config.getServletContext();
        
        //首先分析HTTP header信息判断是否为混合型数据
        String method=request.getMethod();
        String contentType = request.getContentType();
        int contentLen=request.getContentLength();
        //System.out.println("content length="+contentLen);
        
        //判断
        //1.取得请求内容类型如果不是POST,
        //2.如果请求内容不是"multipart/form-data",
        //3.content length必须>0
        Boolean bIsMultiForm=(
        		method.compareToIgnoreCase(METHOD_POST)==0 && 
        		contentType.startsWith(SIGN_MULTIDATA) && 
        		contentLen>0);
        if(bIsMultiForm){
        	//如果以上条件均成立
            //取得boundary
            boundary = getBoundary(contentType);
            //我们为每次上传文件请求活动创建一个Progress Id，主要为了以后Client用此id向Server取得当前上传活动进度信息
            String progId=GlobInfo.getQueryString(request,"progId");
            System.out.println("progId="+progId);
            if(!progId.isEmpty() && !boundary.isEmpty()){                
                //检查最大接收尺寸
                Boolean bParamCheck=true;
                Long maxBytes=(configParam.maxPkgBytes<=0)?N_MAX_CONTENTLEN:(configParam.maxPkgBytes*1024*1024);
                if(contentLen>maxBytes){
                	bParamCheck=false;
                }
                
                if(bParamCheck){
                	//创建进度信息
                	//并第一次更新进度信息为初始化状态
                    ProgressInfo progInfo = new ProgressInfo();
                    progInfo.startTime = new Date();
                    progInfo.curTime = new Date();
                    progInfo.proState = ProgressInfo.ProStateEnum.PROSTATE_NULL;
                    progInfo.totalBytes = contentLen;
                    progInfo.curBytes = 0;
                    ProgressInfo.updateProgressInfo(context, progId, progInfo);
                    System.out.println(progInfo);
                	
			        //取得输入流对象
			        ServletInputStream sis = request.getInputStream();
			        BufferedInputStream bis = new BufferedInputStream(sis);
			        //建立一个FormPart空数组
			        List<FormPart> listFormPart=new ArrayList<FormPart>();
			        //分别传入
			        //输入流对象: 为了从流对象中按字节接收客户端request请求
			        //contentLen:接收的字节数ContentLength)
			        //boundary: Multipart 分割线
			        //listFormPart:空的FormPart数组
			        //progInfo:进度信息对象
			        //progId:本次进度Id
			        Boolean result=parseInputStream(bis,contentLen,boundary,listFormPart,
			        		progInfo,context,progId);
			        //检查文件扩展名是否符合要求，否则删除并设置错误信息
			        checkFileExt(listFormPart);
			        
			        //如果分析过程出错误，则返回系统错误指令
			        if(!result)
			        	progErr=ProgressInfo.ProErrEnum.PROERR_REQ_SYS;
			        //将本次分析包结果列表向后续页面传递
			        request.setAttribute(REQUEST_KEY_PROGERR, progErr);
			        request.setAttribute(REQUEST_KEY_FORMPARTLIST, listFormPart);
			        //从Application对象中，本次请求处理完毕你后删除当前进度信息
			        ProgressInfo.delProgressInfo(context, progId);
                }//end if bParamCheck
            }
        }//end if bIsMultiForm
        
        //fosDebug.close();
        //交给后续filter链继续处理
        filterChain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    	//System.out.println("MultiForm:destroy()");	
    }

}
