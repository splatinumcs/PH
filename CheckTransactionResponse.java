package com.tmb.business;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset; 
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List; 
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import obj.TransResponseModel;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import tmb.com.config.CSVManagementFile;
import tmb.com.config.TmbUtility;
import connect.FileSFTP;

public class CheckTransactionResponse {
	final static Logger logger = Logger.getLogger(CheckTransactionResponse.class);
	private Properties prop;
	private SimpleDateFormat dateFormat;
	private String RM_COLUMN ="RM";
	private int DETAIL_OF_LENGHT =59;
	//private CSVManagementFile
	private  List<TransResponseModel> responseFileList = new ArrayList<>();
	private  String searchRefNo = null;
	private FileSFTP fileSFTP = null;
	private File pathSrc = new File("d:/Users/47758/Desktop/Work_DEV_PH/Response_CIB/"); 
	private String sftpPath = "/paymenthub/UAT/CIB/Outbound";
	private List<TransResponseModel> transList= new ArrayList<TransResponseModel>();

	public CheckTransactionResponse(Properties prop, SimpleDateFormat dateFormat) {
		this.prop = prop;
		this.dateFormat = dateFormat;
	}
	public void process(){
		fileSFTP = new FileSFTP(prop);
		
		String  CIB_FILE_REQ_FORMAT = TmbUtility.getEmptyString(prop.getProperty("CIB_FILE_REQ_FORMAT").trim());
		String  CIB_FILE_REQ_BACKUP = TmbUtility.getEmptyString(prop.getProperty("CIB_FILE_REQ_BACKUP").trim());
		String  CIB_FILE_RES_BACKUP = TmbUtility.getEmptyString(prop.getProperty("CIB_FILE_RES_BACKUP").trim());
		sftpPath = TmbUtility.getEmptyString(prop.getProperty("SFTP_CIB_Outbound").trim());
		String SFTP_CIB_FILE_REQ_DOWNLOAD= TmbUtility.getEmptyString(prop.getProperty("SFTP_CIB_FILE_REQ_DOWNLOAD").trim());
		
		TmbUtility.checkAndCreateFolders(CIB_FILE_REQ_BACKUP);
		TmbUtility.checkAndCreateFolders(CIB_FILE_RES_BACKUP);

		fileSFTP.getFileFromSftpParam(SFTP_CIB_FILE_REQ_DOWNLOAD, CIB_FILE_REQ_BACKUP, CIB_FILE_REQ_FORMAT,false);

		//String descPath = "D:/workspaces/PaymenthubClearFile/REQ_MISRESP_FILE/";
		File des  = new File(CIB_FILE_REQ_BACKUP);

		String  fls[] = des.list();
		transList= new ArrayList<TransResponseModel>();

		logger.debug("Size of REQ="+fls.length);
		
		for(String ss : fls ){
			if(new File(des.getAbsolutePath()+File.separator+ss).isFile()){
				//logger.debug(des.getAbsolutePath()+File.separator+ss);
				List<String> list = CSVManagementFile.readTextFile(des.getAbsolutePath()+File.separator+ss);
				//logger.debug(list.get(0).trim().startsWith(RM_COLUMN));

				if(list!=null&&list.get(0).trim().startsWith(RM_COLUMN)){
					list.stream().filter(f -> ((!TmbUtility.isNull(f)&&!f.trim().startsWith(RM_COLUMN)&&f.length()==DETAIL_OF_LENGHT)))
					.forEach(fn -> { 
						transList.add( new TransResponseModel(fn.substring(0, 20).trim(), fn.substring(20, 40).trim(), fn.substring(40, 48).trim(), fn.substring(48, 56).trim(), fn.substring(56, 59).trim()));
					}); 
					//transList.stream().forEach(fn -> { logger.debug( fn.toString());});
					searchStringFromFile();
					processPutFileOnSFTP();

				}else{
					logger.debug("Pls check format  file.");
				}


			}

		}
		
        try {
        	logger.debug("Remove "+CIB_FILE_RES_BACKUP+"  all files ");
			FileUtils.cleanDirectory(new File(CIB_FILE_RES_BACKUP));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logger.error("Remove "+CIB_FILE_RES_BACKUP+"  all files was failed ."+e.getLocalizedMessage());
		}
		// List<String> listFile = CSVManagementFile.readTextFile(fls[0]);
		logger.info("############# START CHECK_TRANS_RESPONSE ###################");
	}
	private void processPutFileOnSFTP() {
		//CSVManagementFile.fileWriter(fileHeader, fileFooter, dataList, filenameOut);
		
		List<String> dataList = new ArrayList<String>();
		
		if(responseFileList !=null &&responseFileList.size()>0){
			//CSVManagementFile.fileWriter(destinationFolderFileCore, fileName, bytesArray);
			responseFileList.stream().forEach(fl ->
			{ 
				try {
					if(fl.getFileName() !=null){
						boolean status = fileSFTP.putResponseFileToSftp(fl.getFileName(),sftpPath);
						fl.setUploadFile(status);
					  logger.info(fl.getFileName()+" upload status was "+status);
					  dataList.add(fl.toString());
					} 
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			if(dataList !=null &&dataList.size()>0){
				CSVManagementFile.fileWriter(null, null, dataList, System.getProperty("user.dir") + "/LOG/LOGS_PUT_FILE_SFTP_"+TmbUtility.sdfYYYYMMDDHHmmss.format(new Date())+".log");
			} 

		}
		responseFileList = new ArrayList<TransResponseModel>();
		transList= new ArrayList<TransResponseModel>();

	}
	private    void searchStringFromFile() {

		responseFileList = new ArrayList<>();
		transList.stream().forEach(batchNo -> {

			searchRefNo = batchNo.getBatRefNo();
			if(!TmbUtility.isNull(searchRefNo)){
				Arrays.stream(pathSrc.list()).forEach(fb -> { 
					String fileName = pathSrc+File.separator+fb.trim();
					Path path = Paths.get(fileName);


					try(Stream <String> streamOfLines = Files.lines(path,Charset.defaultCharset())) {
						// Filter all male students
						List<String> list = streamOfLines
								.filter(s-> s.contains(searchRefNo) )
								.collect(Collectors.toList());

						if(list !=null&&list.size()>0){
							//logger.info(" searchRefNo = found "+fileName);
							responseFileList.add( new TransResponseModel(searchRefNo,fileName,true));
						} else{
							//logger.info(" searchRefNo not found ");
							responseFileList.add( new TransResponseModel(searchRefNo,null,false));
						}
					}catch(Exception e) {  }


				});
			} 
		});


		logger.info("############# searchStringFromFile ###################");
		searchRefNo = null;
		//responseFileList.forEach(System.out::println);

	}

	public static void readStringFromFile(String fileName,String searchTerm) {

		List<String> list = new ArrayList<>();

		try (Stream<String> stream = Files.lines(Paths.get(fileName))) {

			//1. filter line 3
			//2. convert all content to upper case
			//3. convert it into a List
			stream
			.filter(x -> searchTerm.indexOf(x)>0)
			.forEach(System.out::println);
			//System.out.println("count : "+ count);


		} catch (IOException e) {
			e.printStackTrace();
		}

		list.forEach(System.out::println);

	}


	public static void FileWordCount(String args[]) {
		long wordCount = 0;
		Path textFilePath = Paths.get("C:\\JavaBrahman\\WordCount.txt");
		try {
			Stream<String> fileLines = Files.lines(textFilePath, Charset.defaultCharset());
			wordCount = fileLines.flatMap(line -> Arrays.stream(line.split(" "))).count();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
		System.out.println("Number of words in WordCount.txt: "+ wordCount);
	}





}
