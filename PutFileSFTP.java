package com.tmb.business;

import gen.ClearTempFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import obj.FileSFTPModel;

import org.apache.log4j.Logger;

import tmb.com.config.CSVManagementFile;
import tmb.com.config.TmbUtility;
import connect.FileSFTP;

public class PutFileSFTP {
	final static Logger logger = Logger.getLogger(PutFileSFTP.class);
	private Properties prop;
	private FileSFTP fileSFTP = null;
	private String sourcePathFiles = "D:/app/staging/in/cib/SFTP";
	private String configFile  = System.getProperty("user.dir") + "/config/FileNameAndSFTPConfig.properties";
	private List<FileSFTPModel> fileSFTPModel  = null;
	private int sftpRetryCount = 1;
  
	private ArrayList<String> dataList = new ArrayList<>();
	
	public PutFileSFTP(Properties prop) {
		this.prop = prop; 
	}
	public void process(){
		fileSFTP = new FileSFTP(prop);
		
                sourcePathFiles = TmbUtility.getEmptyString(prop.getProperty("SOURCE_FILE_LOCAL_PATH").trim());
                
		File srcDirFile = new File(sourcePathFiles);

		if(srcDirFile.exists()&&srcDirFile.isDirectory()){
			dataList = new ArrayList<>();
			
			sftpRetryCount = TmbUtility.convertStrToInt(prop.getProperty("SFTP_RETRY_MAX").trim());
			
			
			logger.info("Starting read file on directoy.");
			fileSFTPModel = new ArrayList<FileSFTPModel>();

			List<String> listConfile = CSVManagementFile.readTextFile(configFile);

			listConfile.stream().filter(ob -> !ob.isEmpty())
			.forEach(l -> 
			{
				FileSFTPModel md  =  getFileSFTPModel(l);
				fileSFTPModel.add(md);
			});

			logger.debug(" start find file name for put file sftp  ");
			Date start  = new Date();

			fileSFTPModel.stream().forEach(fml -> {
				fml.setSrcPathFileList(new ArrayList<>());
				Arrays.stream(srcDirFile.list()).filter(fl -> new File(srcDirFile.getAbsolutePath()+File.separator+fl).isFile()  ) 
				.forEach(fln -> {
					File flnTemp = new File(srcDirFile.getAbsolutePath()+File.separator+fln);
					if(flnTemp.getName()!= null){
                                            if(!TmbUtility.isNull(fml.getStartWithFileName())&&
                                                    !TmbUtility.isNull(fml.getEndWithFileName())&&
                                                    flnTemp.getName().startsWith(fml.getStartWithFileName())&&
                                                    flnTemp.getName().endsWith(fml.getEndWithFileName()) ){
                                                fml.getSrcPathFileList().add(srcDirFile.getAbsolutePath()+File.separator+fln);
                                                logger.debug("case 1 ="+fml.getPathSftp()+"flnTemp.getName()= "+flnTemp.getName()+" StartWith ="+fml.getStartWithFileName()+" endwith="+fml.getEndWithFileName());
                                            }else if(!TmbUtility.isNull(fml.getStartWithFileName())&&
                                                    TmbUtility.isNull(fml.getEndWithFileName())&&
                                                    flnTemp.getName().startsWith(fml.getStartWithFileName())){
                                                fml.getSrcPathFileList().add(srcDirFile.getAbsolutePath()+File.separator+fln);
                                                logger.debug("case 1 ="+fml.getPathSftp()+"flnTemp.getName()= "+flnTemp.getName()+" StartWith ="+fml.getStartWithFileName()+" endwith="+fml.getEndWithFileName());
                                            }
						
					}

				} );
			});

			logger.debug(" End find file name for put file sftp  "+TmbUtility.diffTimeStr(start, new Date()));
			logger.debug("Used "+TmbUtility.diffTimeStr(start, new Date()));

                        fileSFTPModel.stream().forEach( fp -> {
                            logger.debug("PathSftp =="+fp.getPathSftp());
                            fp.getSrcPathFileList().stream().forEach(fpp -> logger.debug("file name =="+fpp) );
                        });
			logger.debug(" start put file on  sftp  ");
			Date start1  = new Date();

			fileSFTPModel.stream().forEach(fml -> {

				logger.info(" SFTP PATH ="+fml.getFormatFileName()); 
				if(fml.getSrcPathFileList()!=null){
					fml.getSrcPathFileList().stream().forEach(fml2 -> {
						logger.info(" =SrcPathFileList = "+fml2); 
						int cnt = 0;
						while(!pastFileToSftpPath(fml2,fml.getPathSftp())&&cnt<=sftpRetryCount){
							cnt+=1;
						}

					});
				}

			});
			
			if(dataList !=null &&dataList.size()>0){
				CSVManagementFile.fileWriter(null, null, dataList, System.getProperty("user.dir") + "/LOG/LOGS_AUTO_PUT_FILE_SFTP_"+TmbUtility.sdfYYYYMMDDHHmmss.format(new Date())+".log");
			} 
			
			logger.debug(" end put file on  sftp  ");
			logger.debug("Used "+TmbUtility.diffTimeStr(start1, new Date()));

			logger.info("End read file on directory.");
		}else{
			logger.error(" Directory doesn't exist or no directory  . Pls check path of  this directory.");
		}

	}
	public boolean pastFileToSftpPath(String scrPath,String sftpPath) {
		boolean isSuccess = true;
		String dt = "";
		try { 
			if(!scrPath.isEmpty()&&!scrPath.isEmpty()){
				isSuccess = fileSFTP.putResponseFileToSftp(scrPath,sftpPath);
				dt = TmbUtility.getCurrentDateSimpleDateFormat(TmbUtility.sdfYYYYMMDDHHmmss)+" Source File = "+scrPath +" sftp Path= "+sftpPath+" status upload was "+(isSuccess?"Successfully.":"failed");
				if(isSuccess){
					logger.debug("Clear File name "+scrPath+" was done .");
					ClearTempFile.delete(new File(scrPath));
				}
			}else{
				logger.debug("Scr Path is empty or sftp Path is empty .");
				dt = TmbUtility.getCurrentDateSimpleDateFormat(TmbUtility.sdfYYYYMMDDHHmmss)+" Source File = "+scrPath +" sftp Path= "+sftpPath+" status upload was  failed [Scr Path is empty or sftp Path is empty ]. ";
			}

		} catch (Exception e) { 
			e.printStackTrace(); 
			fileSFTP = new FileSFTP(prop);
			logger.error(e.getLocalizedMessage());
			isSuccess = false;
			dt = TmbUtility.getCurrentDateSimpleDateFormat(TmbUtility.sdfYYYYMMDDHHmmss)+" Source File = "+scrPath +" sftp Path= "+sftpPath+" status upload was  failed ["+e.getLocalizedMessage()+" ]. ";
		}finally{
			
		}
		//String dt = "ScrFile = "+scrPath +" sftpPath= "+sftpPath+" status upload was "+(isSuccess?"Successfully.":"failed");
		dataList.add(dt);
		return isSuccess;
	}
	public FileSFTPModel getFileSFTPModel(String lineData) {
		FileSFTPModel model = new FileSFTPModel();
		String [] sp1 = spiltFileName(lineData," "); 
		if(sp1.length>=2){
			model.setPathSftp(sp1[0]);
			model.setFormatFileName(sp1[1]);
			String [] sp2 = spiltFileName(sp1[1],"*");
			if(sp2.length>=2){ 
				model.setStartWithFileName(sp2[0]);
				model.setEndWithFileName(sp2[1]);
			}else if(sp2.length >=1 ){ 
				model.setStartWithFileName(sp2[0]); 
			}
		}
		if(model.getPathSftp().isEmpty()|| model.getFormatFileName().isEmpty()){
			model.setRemarks("Some field is empty (PathSftp,FormatFileName).");
			return null;
		}else{
			return model;
		} 
	}
	public String [] spiltFileName(String lineData,String spilt) {
		String [] model = new String [2];

		StringTokenizer stp = new StringTokenizer(lineData,spilt);
		int r = 0;
		while(stp.hasMoreElements()){
			String tmp = stp.nextToken();
			String data = !tmp.isEmpty()?tmp.toString().trim():""; 
			if(r == 0){
				model [0] =data ;
			}else if(r == 1){
				model [1] =data ;
			}
			r += 1;

		} 
		return model;

	}
}
