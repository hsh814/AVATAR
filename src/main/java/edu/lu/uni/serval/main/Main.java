package edu.lu.uni.serval.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.json.JSONObject;

import java.io.IOException;

import edu.lu.uni.serval.avatar.AbstractFixer;
import edu.lu.uni.serval.avatar.Avatar;
import edu.lu.uni.serval.config.Configuration;

import edu.lu.uni.serval.utils.ShellUtils;
import edu.lu.uni.serval.utils.FileHelper;

/**
 * Fix bugs with Fault Localization results.
 * 
 * @author kui.liu
 *
 */
public class Main {
	
	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("Arguments: <Buggy_Project_Path> <defects4j_Path> <Bug_ID> <FL_Metric>");
			System.exit(0);
		}
		String buggyProjectsPath = args[0];// "../Defects4JData/"
		String defects4jPath = args[1]; // "../defects4j/"
		String projectName = args[2]; // "Chart_1"
		Configuration.faultLocalizationMetric = args[3];
		Configuration.outputPath += "FL/";
		System.out.println(projectName);
		fixBug(buggyProjectsPath, defects4jPath, projectName);
	}

	public static void setTestInfo(JSONObject jsonObject, String buggyProjectsPath, String defects4jPath, String buggyProjectName, String proj, int id) {
		try {// Compile patched file.
			File buggyProject = new File(buggyProjectsPath + "/" + buggyProjectName);
			String workDir = buggyProject.getAbsolutePath();
			if (!buggyProject.exists()) {
				ShellUtils.shellRun(Arrays.asList("defects4j checkout -p " + proj + " -v " + id + "b -w " + workDir),
						buggyProject.getAbsolutePath() + "-checkout");
				ShellUtils.shellRun(Arrays.asList("defects4j compile -w " + workDir), buggyProjectName + "-compile");
			}
			File outDir = new File("d4j/" + buggyProjectName);
			outDir.mkdirs();
			String outFileName = "d4j/" + buggyProjectName + "/tests.trigger";
			String outFileNameAll = "d4j/" + buggyProjectName + "/tests.all";
			File outFile = new File(outFileName);
			File outFileAll = new File(outFileNameAll);
			if (!(outFile.exists() && outFileAll.exists())) {
				ShellUtils.shellRun(
						Arrays.asList("defects4j export -w " + workDir + " -p tests.trigger -o " + outFileName),
						buggyProjectName + "-test");
				ShellUtils.shellRun(
						Arrays.asList("defects4j export -w " + workDir + " -p tests.all -o " + outFileNameAll),
						buggyProjectName + "-test-all");
			}

			HashSet<String> testSet = new HashSet<>();
			String out = FileHelper.readFile(outFile);
			ArrayList<String> failTests = new ArrayList<>(); 
			String[] lines = out.split("\n");
			for (String line : lines) {
				String test = line.split("::")[0];
				if (testSet.contains(test)) {
					continue;
				}
				testSet.add(test);
				failTests.add(test);
			}
			jsonObject.put("failing_test_cases", failTests);
			String all = FileHelper.readFile(outFileAll);
			jsonObject.put("passing_test_cases", all.split("\n"));
		} catch (IOException e) {
			// log.debug(buggyProject + " ---Fixer: fix fail because of javac exception! ");
			// continue;
		}
	}

	public static void fixBug(String buggyProjectsPath, String defects4jPath, String buggyProjectName) {
		String suspiciousFileStr = Configuration.suspPositionsFilePath;
		
		String dataType = "AVATAR";
		String[] elements = buggyProjectName.split("_");
		String projectName = elements[0];
		int bugId;
		try {
			bugId = Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.err.println("Please input correct buggy project ID, such as \"Chart_1\".");
			return;
		}
		
		AbstractFixer fixer = new Avatar(buggyProjectsPath, projectName, bugId, defects4jPath);
		fixer.jsonObject.put("project_name", buggyProjectName);
		setTestInfo(fixer.jsonObject, buggyProjectsPath, defects4jPath, buggyProjectName, projectName, bugId);

		fixer.metric = Configuration.faultLocalizationMetric;
		fixer.dataType = dataType;
		fixer.suspCodePosFile = new File(suspiciousFileStr);
		if (Integer.MAX_VALUE == fixer.minErrorTest) {
			System.out.println("Failed to defects4j compile bug " + buggyProjectName);
			return;
		}
		
		fixer.fixProcess();
		
		int fixedStatus = fixer.fixedStatus;
		switch (fixedStatus) {
		case 0:
			System.out.println("Failed to fix bug " + buggyProjectName);
			break;
		case 1:
			System.out.println("Succeeded to fix bug " + buggyProjectName);
			break;
		case 2:
			System.out.println("Partial succeeded to fix bug " + buggyProjectName);
			break;
		}
	}

}
