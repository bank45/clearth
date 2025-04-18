/******************************************************************************
 * Copyright 2009-2024 Exactpro Systems Limited
 * https://www.exactpro.com
 * Build Software to Test Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.exactprosystems.clearth.automation.report;

import com.exactprosystems.clearth.ClearThCore;
import com.exactprosystems.clearth.automation.Action;
import com.exactprosystems.clearth.automation.actions.macro.MacroAction;
import com.exactprosystems.clearth.automation.report.html.HtmlActionReport;
import com.exactprosystems.clearth.automation.report.html.template.ReportTemplatesProcessor;
import com.exactprosystems.clearth.utils.JsonMarshaller;
import com.exactprosystems.clearth.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.exactprosystems.clearth.ClearThCore.rootRelative;
import static com.exactprosystems.clearth.automation.report.ReportFormat.HTML;
import static com.exactprosystems.clearth.automation.report.ReportFormat.JSON;

public class ActionReportWriter
{
	private static final Logger logger = LoggerFactory.getLogger(ActionReportWriter.class);

	public static final String HTML_SUFFIX = ".html",
    		JSON_SUFFIX = ".json",
    		REPORT_FILENAME = "report",
    		HTML_REPORT_NAME = REPORT_FILENAME+HTML_SUFFIX,
    		JSON_REPORT_NAME = REPORT_FILENAME+JSON_SUFFIX,
    		FAILED_SUFFIX = "_failed",
    		HTML_FAILED_REPORT_NAME = REPORT_FILENAME + FAILED_SUFFIX + HTML_SUFFIX;

	private int actionIndex = 0;
	private final ReportsConfig reportsConfig;
	private final ReportTemplatesProcessor templatesProcessor;
	
	public ActionReportWriter(ReportsConfig reportsConfig, ReportTemplatesProcessor templatesProcessor)
	{
		this.reportsConfig = reportsConfig;
		this.templatesProcessor = templatesProcessor;
	}
	
	public void reset()
	{
		actionIndex = 0;
	}
	
	/**
	 * Writes HTML report to file where other action reports are stored for given matrix and given step.
	 * @param action to write report for
	 * @param actionsReportsDir path to directory with execution reports data. Action report file will be located in it
	 * @param stepFileName name of file with action reports for particular step
	 */
	public void writeReport(Action action, String actionsReportsDir, String stepFileName)
	{
		incActionIndex();
		if (action.getResult() != null)
			action.getResult().processDetails(getReportDir(actionsReportsDir, action), action);

		if (reportsConfig.isCompleteHtmlReport())
			writeHtmlActionReport(action, actionsReportsDir, stepFileName, false);
		if (reportsConfig.isFailedHtmlReport() && (!action.isPassed() || action.isAsync()))
			writeHtmlActionReport(action, actionsReportsDir, stepFileName, true);

		if (reportsConfig.isCompleteJsonReport())
			writeJsonActionReport(action, actionsReportsDir, stepFileName);
	}
	
	/**
	 * Updates report files to allow addition of data to them
	 * @param actionsReportsDir path to directory with execution reports data
	 * @param matrixReportsDir name of directory with action reports for particular matrix
	 * @param stepFileName name of file with action reports for particular step
	 * @throws IOException when report file update failed
	 */
	public void prepareReportsToUpdate(String actionsReportsDir, String matrixReportsDir, String stepFileName) throws IOException
	{
		if (!reportsConfig.isCompleteJsonReport())
			return;
		
		prepareJsonReportToUpdate(actionsReportsDir, matrixReportsDir, stepFileName);
	}
	
	
	protected void writeJsonActionReport(Action action, String actionsReportsDir, String actionsReportFile)
	{
		File reportFile = getJsonStepReport(actionsReportsDir, action.getMatrix().getShortFileName(), actionsReportFile);
		
		PrintWriter writer = null;
		try
		{
			writer = createReportWriter(reportFile);
			if (reportFile.length() == 0)
				writer.println("[");
			else
				writer.println(",");
			
			ActionReport actionReport = createActionReport(action);
			String jsonActionReport = new JsonMarshaller<ActionReport>().marshal(actionReport);
			
			if (!action.isAsync() || action.isPayloadFinished())
				writer.println(jsonActionReport);
			else
			{
				writePreReportData(writer, action, JSON);
				writer.println(jsonActionReport);
				writePostReportData(writer, action, JSON);
			}
		}
		catch (Exception e)
		{
			getLogger().error("Error occurred while writing JSON action report", e);
		}
		finally
		{
			if (writer != null)
			{
				writer.flush();
				writer.close();
			}
		}
	}

	/**
	 * Updates already written reports with actual result of asynchronous actions.
	 * @param actions asynchronous actions whose reports to update
	 * @param actionsReportsDir path to directory with execution reports data. Action report file is located in it
	 */
	public void updateReports(Collection<Action> actions, String actionsReportsDir)
	{
		getLogger().debug("Updating reports for {} action(s)", actions.size());
		
		Map<MatrixStep, Collection<ActionUpdate>> actionsByMatrixStep = prepareActionsToUpdate(actions);
		for (Entry<MatrixStep, Collection<ActionUpdate>> group : actionsByMatrixStep.entrySet())
		{
			MatrixStep key = group.getKey();
			File reportDir = getReportDir(actionsReportsDir, key.getMatrix().getShortFileName());
			String actionsReportFile = key.getStep().getSafeName();
			Collection<ActionUpdate> actionUpdates = group.getValue();
			for (ActionUpdate au : actionUpdates)
			{
				Action action = au.getAction();
				if (action.getResult() != null)
					action.getResult().processDetails(reportDir, action);
			}
			
			if (reportsConfig.isCompleteHtmlReport())
				updateHtmlReport(actionUpdates, reportDir, actionsReportFile, false);
			if (reportsConfig.isFailedHtmlReport())
				updateHtmlReport(actionUpdates, reportDir, actionsReportFile, true);
			if (reportsConfig.isCompleteJsonReport())
				updateJsonReport(actionUpdates, reportDir, actionsReportFile);
		}
	}
	
	private Map<MatrixStep, Collection<ActionUpdate>> prepareActionsToUpdate(Collection<Action> actions)
	{
		Map<MatrixStep, Collection<ActionUpdate>> result = new LinkedHashMap<>();
		for (Action a : actions)
		{
			MatrixStep key = new MatrixStep(a.getMatrix(), a.getStep());
			int index = incActionIndex();
			ActionUpdate au = new ActionUpdate(a, index);
			result.computeIfAbsent(key, k -> new ArrayList<>()).add(au);
		}
		return result;
	}
	
	protected void updateHtmlReport(Collection<ActionUpdate> actions, File reportDir, String actionsReportFile, boolean onlyFailed)
	{
		File reportFile = new File(reportDir, actionsReportFile+".swp"),
				originalReportFile = getReportFile(reportDir, actionsReportFile, onlyFailed);
		if (!updateReport(originalReportFile, actions, actionsReportFile, reportFile, HTML, reportDir, onlyFailed))
			return;
		replaceReportFile(reportFile, originalReportFile);
	}
	
	protected void updateJsonReport(Collection<ActionUpdate> actions, File reportDir, String actionsReportFile)
	{
		File reportFile = new File(reportDir, actionsReportFile+".swp"),
				originalReportFile = getJsonStepReport(reportDir, actionsReportFile);
		if (!updateReport(originalReportFile, actions, "", reportFile, JSON, reportDir, false))
			return;
		replaceReportFile(reportFile, originalReportFile);
	}

	private void replaceReportFile(File reportFile, File originalReportFile)
	{
		if (!originalReportFile.delete())
		{
			getLogger().error("Could not delete original report file '{}'", originalReportFile.getAbsolutePath());
			return;
		}

		if (!reportFile.renameTo(originalReportFile))
			getLogger().error("Could not rename updated report file '{}' to '{}'",
					reportFile.getAbsolutePath(), originalReportFile.getAbsolutePath());
	}
	
	private File getJsonStepReport(String actionsReportsDir, String matrixFileName, String stepReportFile)
	{
		File reportDir = getReportDir(actionsReportsDir, matrixFileName);
		return getJsonStepReport(reportDir, stepReportFile);
	}
	
	private File getJsonStepReport(File reportDir, String stepReportFile)
	{
		return new File(reportDir, stepReportFile + JSON_SUFFIX);
	}
	
	protected Logger getLogger()
	{
		return logger;
	}
	
	public int getActionIndex()
	{
		return actionIndex;
	}
	
	public int incActionIndex()
	{
		actionIndex++;
		return actionIndex;
	}
	
	
	protected String buildResultId(String actionsReportFile)
	{
		return buildResultId(actionsReportFile, actionIndex);
	}
	
	protected String buildResultId(String actionsReportFile, int index)
	{
		return actionsReportFile+"_action_"+index;
	}
	
	protected File getReportDir(String actionsReportsDir, Action action)
	{
		return getReportDir(actionsReportsDir, action.getMatrix().getShortFileName());
	}
	
	protected File getReportDir(String actionsReportsDir, String matrixShortName)
	{
		Path reportDir = Path.of(ClearThCore.appRootRelative(actionsReportsDir), matrixShortName);
		try
		{
			if (!Files.exists(reportDir))
				Files.createDirectories(reportDir);
		}
		catch (IOException e)
		{
			getLogger().error("Could not create directory "+reportDir, e);
		}
		
		return reportDir.toFile();
	}
	
	protected File getReportFile(File reportDir, String actionsReportFile, boolean onlyFailed)
	{
		if (onlyFailed)
			actionsReportFile += FAILED_SUFFIX;
		return new File(reportDir, actionsReportFile);
	}
	
	protected BufferedReader createReportReader(File reportFile) throws IOException
	{
		return new BufferedReader(new FileReader(reportFile));
	}
	
	protected PrintWriter createReportWriter(File reportFile) throws IOException
	{
		return new PrintWriter(new BufferedWriter(new FileWriter(reportFile, true)));
	}
	
	protected HtmlActionReport createHtmlActionReport()
	{
		return new HtmlActionReport(templatesProcessor);
	}

	protected ActionReport createActionReport(Action action)
	{
		return !(action instanceof MacroAction) ? new ActionReport(action, this)
				: new MacroActionReport((MacroAction)action, this);
	}

	protected ActionReport createActionReport()
	{
		return new ActionReport();
	}
	
	protected String buildAsyncActionStartComment(Action action, ReportFormat reportFormat)
	{
		String label = "ASYNC action " + action.getIdInMatrix() + " start";
		return wrapCommentLabelByFormat(label, reportFormat);
	}
	
	protected String buildAsyncActionEndComment(Action action, ReportFormat reportFormat)
	{
		String label = "ASYNC action " + action.getIdInMatrix() + " end";
		return wrapCommentLabelByFormat(label, reportFormat);
	}

	protected String wrapCommentLabelByFormat(String label, ReportFormat reportFormat)
	{
		switch (reportFormat)
		{
			case HTML: return "<!-- " + label + " -->";
			case JSON: return "/* " + label + " */";
			default: return "";
		}
	}

	protected void writePreReportData(PrintWriter writer, Action action, ReportFormat reportFormat)
	{
		writer.println(buildAsyncActionStartComment(action, reportFormat));
	}

	protected void writePostReportData(PrintWriter writer, Action action, ReportFormat reportFormat)
	{
		writer.println(buildAsyncActionEndComment(action, reportFormat));
	}

	protected void writeHtmlActionReport(Action action, String actionsReportsDir, String actionsReportFile, boolean onlyFailed)
	{
		if (getLogger().isTraceEnabled())
			getLogger().trace(action.getDescForLog("Writing report for"));
		String resultId = buildResultId(actionsReportFile);
		File reportDir = getReportDir(actionsReportsDir, action),
				reportFile = getReportFile(reportDir, actionsReportFile, onlyFailed);
		PrintWriter writer = null;
		try
		{
			writer = createReportWriter(reportFile);
			HtmlActionReport report = createHtmlActionReport();

			if (!action.isAsync() || action.isPayloadFinished())
			{
				report.write(writer, action, resultId, reportDir, onlyFailed);
			}
			else
			{
				writePreReportData(writer, action, HTML);
				report.write(writer, action, resultId, reportDir, onlyFailed);
				writePostReportData(writer, action, HTML);
			}
		}
		catch (IOException e)
		{
			getLogger().error("Could not write action report", e);
		}
		finally
		{
			if (writer != null)
			{
				writer.flush();
				writer.close();
			}
		}
	}
	
	protected boolean updateReport(File originalReportFile, Collection<ActionUpdate> actions, String actionsReportFile, File updatedReportFile,
								   ReportFormat reportFormat, File reportDir, boolean onlyFailed)
	{
		Map<String, ActionUpdate> actionsByComment = actions.stream()
				.collect(Collectors.toMap(au -> buildAsyncActionStartComment(au.getAction(), reportFormat),
						Function.identity()));
		
		boolean startFound = false,
				endFound = true;
		BufferedReader reader = null;
		PrintWriter writer = null;
		try
		{
			reader = createReportReader(originalReportFile);
			writer = createReportWriter(updatedReportFile);
			
			//Searching for special comments to find action data.
			//All lines between them will be skipped and thus replaced with actual action data. All other lines are kept.
			
			String line,
					endToFind = null;
			while ((line = reader.readLine()) != null)
			{
				if (!startFound)
				{
					ActionUpdate au = actionsByComment.remove(line);
					if (au == null)
					{
						writer.println(line);
						continue;
					}
					
					startFound = true;
					endFound = false;
					
					Action action = au.getAction();
					endToFind = buildAsyncActionEndComment(action, reportFormat);
					if (!onlyFailed || !action.isPassed())
					{
						String resultId = buildResultId(actionsReportFile, au.getIndex());
						writeActionReport(action, reportFormat, writer, resultId, reportDir, onlyFailed);
					}
				}
				else if (!endFound)
				{
					if (!line.equals(endToFind))
						continue;
					
					endFound = true;
					startFound = false;
				}
				else
					writer.println(line);
			}
			
			if (actionsByComment.size() > 0)
			{
				//Storing action results anyway to restore later, if needed
				for (ActionUpdate au : actionsByComment.values())
				{
					String resultId = buildResultId(actionsReportFile, au.getIndex());
					writeActionReport(au.getAction(), reportFormat, writer, resultId, reportDir, onlyFailed);
				}
			}
		}
		catch (IOException e)
		{
			logger.error("Could not write action report", e);
			return false;  //On error the report can't be updated and current report should remain intact
		}
		finally
		{
			if (writer != null)
			{
				writer.flush();
				writer.close();
			}
			
			Utils.closeResource(reader);
		}
		
		if (!endFound)
		{
			logger.warn("Async action end not found. Report is not updated to not affect other data");
			return false;
		}
		if (actionsByComment.size() > 0)
		{
			logger.warn("Async action start not found for {} action(s). Report is not updated", actionsByComment.size());
			return false;
		}
		
		return true;
	}

	protected ActionReport createActionReportForUpdate(Action action, ReportFormat format)
	{
		switch (format)
		{
			case HTML: return createHtmlActionReport();
			case JSON: return createActionReport(action);
			default: throw new IllegalArgumentException("Unknown report format specified for action report update: "+format);
		}
	}

	protected void writeActionReport(Action action, ReportFormat format, PrintWriter writer,
									 String containerId, File reportDir, boolean onlyFailed) throws IOException
	{
		ActionReport report = createActionReportForUpdate(action, format);
		switch (format)
		{
			case HTML:
				((HtmlActionReport)report).write(writer, action, containerId, reportDir, onlyFailed);
				return;
			
			case JSON:
				String reportStr = new JsonMarshaller<ActionReport>().marshal(report);
				writer.println(reportStr);
				return;
		}
	}

	public void makeReportsEnding(String actionsReportsDir, String stepSafeName)
	{
		File[] files = new File(rootRelative(actionsReportsDir)).listFiles();
		if (files == null || files.length == 0)
			return;

		for (File file : files)
		{
			if (!file.isDirectory())
				continue;

			completeStepJsonReport(actionsReportsDir, file.getName(), stepSafeName);
		}
	}

	protected void completeStepJsonReport(String actionsReportsDir, String matrixFileName, String stepSafeName)
	{
		File reportFile = getJsonStepReport(actionsReportsDir, matrixFileName, stepSafeName);
		if (!reportFile.isFile())
			return;

		PrintWriter writer = null;
		try
		{
			writer = new PrintWriter(new FileWriter(reportFile, true));
			writer.println("]");
		}
		catch (IOException e)
		{
			getLogger().error("Cannot complete step json report", e);
		}
		finally
		{
			if (writer != null)
			{
				writer.flush();
				writer.close();
			}
		}
	}
	
	
	private void prepareJsonReportToUpdate(String actionsReportsDir, String matrixReportsDir, String stepFileName) throws IOException
	{
		File reportFile = getJsonStepReport(actionsReportsDir, matrixReportsDir, stepFileName),
				tempFile = File.createTempFile(reportFile.getName()+"_", ".tmp", reportFile.getParentFile());
		try (BufferedReader reader = new BufferedReader(new FileReader(reportFile));
				BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile)))
		{
			String skipped = null,
					line = null;
			while ((line = reader.readLine()) != null)
			{
				if (skipped != null)
				{
					writer.write(skipped);
					writer.newLine();
					skipped = null;
				}
				
				if (line.equals("]"))
					skipped = line;
				else
				{
					writer.write(line);
					writer.newLine();
				}
			}
		}
		catch (Exception e)
		{
			Files.delete(tempFile.toPath());
			throw e;
		}
		
		Files.move(tempFile.toPath(), reportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
}
