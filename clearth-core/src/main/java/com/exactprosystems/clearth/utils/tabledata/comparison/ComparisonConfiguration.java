/******************************************************************************
 * Copyright 2009-2025 Exactpro Systems Limited
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

package com.exactprosystems.clearth.utils.tabledata.comparison;

import com.exactprosystems.clearth.automation.exceptions.ParametersException;
import com.exactprosystems.clearth.utils.IValueTransformer;
import com.exactprosystems.clearth.utils.XmlUtils;
import com.exactprosystems.clearth.utils.inputparams.ParametersHandler;
import com.exactprosystems.clearth.utils.tabledata.comparison.mappings.DataMapping;
import com.exactprosystems.clearth.utils.tabledata.comparison.mappings.StringDataMapping;
import com.exactprosystems.clearth.utils.tabledata.comparison.mappings.descs.MappingDesc;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.exactprosystems.clearth.utils.tabledata.comparison.ComparisonProcessor.DEFAULT_MIN_STORED_ROWS_COUNT;
import static com.exactprosystems.clearth.utils.tabledata.comparison.ComparisonProcessor.DEFAULT_MAX_STORED_ROWS_COUNT;
import static com.exactprosystems.clearth.utils.tabledata.comparison.ComparisonProcessor.DEFAULT_MAX_ROWS_TO_SHOW_COUNT;

public class ComparisonConfiguration
{
	private static final String MIN_ROWS_TO_STORE_TEMPLATE = "Min%sRowsInReport",
			MAX_ROWS_TO_STORE_TEMPLATE = "Max%sRowsInReport",
			MAX_ROWS_TO_SHOW_TEMPLATE = "Max%sRowsToShow";
	public static final String KEY_COLUMNS = "KeyColumns",
			KEY_VALUES_IN_HEADER = "KeyValuesInHeader",
			NUMERIC_COLUMNS = "NumericColumns",
			MAPPING_FILE_NAME = "MappingFileName",
			CHECK_DUPLICATES = "CheckDuplicates",
			LIST_FAILED_COLUMNS = "ListFailedColumnsInReport",
			FAIL_UNEXPECTED_COLUMNS = "FailUnexpectedColumns",
			PASSED = "Passed",
			FAILED = "Failed",
			NOT_FOUND = "NotFound",
			EXTRA = "Extra",
			
			MIN_PASSED_ROWS_TO_STORE = String.format(MIN_ROWS_TO_STORE_TEMPLATE, PASSED),
			MAX_PASSED_ROWS_TO_STORE = String.format(MAX_ROWS_TO_STORE_TEMPLATE, PASSED),
			MAX_PASSED_ROWS_TO_SHOW = String.format(MAX_ROWS_TO_SHOW_TEMPLATE, PASSED),
			
			MIN_FAILED_ROWS_TO_STORE = String.format(MIN_ROWS_TO_STORE_TEMPLATE, FAILED),
			MAX_FAILED_ROWS_TO_STORE = String.format(MAX_ROWS_TO_STORE_TEMPLATE, FAILED),
			MAX_FAILED_ROWS_TO_SHOW = String.format(MAX_ROWS_TO_SHOW_TEMPLATE, FAILED),
			
			MIN_NOT_FOUND_ROWS_TO_STORE = String.format(MIN_ROWS_TO_STORE_TEMPLATE, NOT_FOUND),
			MAX_NOT_FOUND_ROWS_TO_STORE = String.format(MAX_ROWS_TO_STORE_TEMPLATE, NOT_FOUND),
			MAX_NOT_FOUND_ROWS_TO_SHOW = String.format(MAX_ROWS_TO_SHOW_TEMPLATE, NOT_FOUND),
			
			MIN_EXTRA_ROWS_TO_STORE = String.format(MIN_ROWS_TO_STORE_TEMPLATE, EXTRA),
			MAX_EXTRA_ROWS_TO_STORE = String.format(MAX_ROWS_TO_STORE_TEMPLATE, EXTRA),
			MAX_EXTRA_ROWS_TO_SHOW = String.format(MAX_ROWS_TO_SHOW_TEMPLATE, EXTRA);
	
	protected final IValueTransformer bdValueTransformer;
	protected Set<String> keyColumns;
	protected Map<String, BigDecimal> numericColumns;
	protected DataMapping<String> dataMapping;
	
	protected boolean checkDuplicates,
			listFailedColumns,
			keyValuesInHeader,
			failUnexpectedColumns;
	
	protected ComparisonRowsConfiguration passedRowsConfig,
			failedRowsConfig,
			notFoundRowsConfig,
			extraRowsConfig;
	
	public ComparisonConfiguration(Map<String, String> parameters, IValueTransformer bdValueTransformer) throws ParametersException
	{
		this.bdValueTransformer = bdValueTransformer;
		
		ParametersHandler handler = new ParametersHandler(parameters);
		loadConfiguration(handler);
		handler.check();
	}
	
	protected void loadConfiguration(ParametersHandler handler) throws ParametersException
	{
		keyColumns = handler.getSet(KEY_COLUMNS, ",");
		numericColumns = getNumericColumns(handler.getSet(NUMERIC_COLUMNS, ","));
		File mappingFile = handler.getFile(MAPPING_FILE_NAME);
		if (mappingFile != null)
		{
			if (!CollectionUtils.isEmpty(keyColumns) || !MapUtils.isEmpty(numericColumns))
				throw new ParametersException("Parameters '" + KEY_COLUMNS + "' and '" + NUMERIC_COLUMNS +
						"' are not compatible with parameter '" + MAPPING_FILE_NAME + "'");

			dataMapping = getDataMapping(mappingFile);
			keyColumns = dataMapping.getKeyColumns();
			numericColumns = dataMapping.getNumericColumns();
		}

		checkDuplicates = handler.getBoolean(CHECK_DUPLICATES, false);
		listFailedColumns = handler.getBoolean(LIST_FAILED_COLUMNS, false);
		keyValuesInHeader = handler.getBoolean(KEY_VALUES_IN_HEADER, false);
		failUnexpectedColumns = handler.getBoolean(FAIL_UNEXPECTED_COLUMNS, false);
		
		passedRowsConfig = new ComparisonRowsConfiguration(handler.getInteger(MIN_PASSED_ROWS_TO_STORE, DEFAULT_MIN_STORED_ROWS_COUNT),
				handler.getInteger(MAX_PASSED_ROWS_TO_STORE, DEFAULT_MAX_STORED_ROWS_COUNT),
				handler.getInteger(MAX_PASSED_ROWS_TO_SHOW, DEFAULT_MAX_ROWS_TO_SHOW_COUNT));
		
		failedRowsConfig = new ComparisonRowsConfiguration(handler.getInteger(MIN_FAILED_ROWS_TO_STORE, DEFAULT_MIN_STORED_ROWS_COUNT),
				handler.getInteger(MAX_FAILED_ROWS_TO_STORE, DEFAULT_MAX_STORED_ROWS_COUNT),
				handler.getInteger(MAX_FAILED_ROWS_TO_SHOW, DEFAULT_MAX_ROWS_TO_SHOW_COUNT));
		
		notFoundRowsConfig = new ComparisonRowsConfiguration(handler.getInteger(MIN_NOT_FOUND_ROWS_TO_STORE, DEFAULT_MIN_STORED_ROWS_COUNT),
				handler.getInteger(MAX_NOT_FOUND_ROWS_TO_STORE, DEFAULT_MAX_STORED_ROWS_COUNT),
				handler.getInteger(MAX_NOT_FOUND_ROWS_TO_SHOW, DEFAULT_MAX_ROWS_TO_SHOW_COUNT));
		
		extraRowsConfig = new ComparisonRowsConfiguration(handler.getInteger(MIN_EXTRA_ROWS_TO_STORE, DEFAULT_MIN_STORED_ROWS_COUNT),
				handler.getInteger(MAX_EXTRA_ROWS_TO_STORE, DEFAULT_MAX_STORED_ROWS_COUNT),
				handler.getInteger(MAX_EXTRA_ROWS_TO_SHOW, DEFAULT_MAX_ROWS_TO_SHOW_COUNT));
	}

	protected DataMapping<String> getDataMapping(File file)
			throws ParametersException
	{
		if (file == null)
			return null;

		try
		{
			MappingDesc mappingDesc = XmlUtils.unmarshalObject(MappingDesc.class, file.getCanonicalPath());
			return new StringDataMapping(mappingDesc);
		}
		catch (Exception e)
		{
			throw new ParametersException("Error while loading data mapping file " + file.getAbsolutePath(), e);
		}
	}

	protected Map<String, BigDecimal> getNumericColumns(Set<String> columnsWithScales) throws ParametersException
	{
		Map<String, BigDecimal> numericColumns = new HashMap<>();
		for (String columnWithScale : columnsWithScales)
		{
			String[] columnAndScale = columnWithScale.split(":", 2);
			String column = columnAndScale[0];
			BigDecimal precision = BigDecimal.ZERO;
			if (columnAndScale.length == 2)
			{
				try
				{
					precision = new BigDecimal(bdValueTransformer != null ?
							bdValueTransformer.transform(columnAndScale[1]) : columnAndScale[1]);
				}
				catch (Exception e)
				{
					throw new ParametersException("Numeric column '" + column + "' with specified precision '"
							+ columnAndScale[1] + "' couldn't be obtained due to error.", e);
				}
			}
			numericColumns.put(column, precision);
		}
		return numericColumns;
	}
	
	public DataMapping<String> getDataMapping()
	{
		return dataMapping;
	}
	
	public Set<String> getKeyColumns()
	{
		return keyColumns;
	}
	
	public Map<String, BigDecimal> getNumericColumns()
	{
		return numericColumns;
	}
	
	public boolean isCheckDuplicates()
	{
		return checkDuplicates;
	}
	
	public boolean isKeyValuesInHeader()
	{
		return keyValuesInHeader;
	}
	
	public boolean isListFailedColumns()
	{
		return listFailedColumns;
	}
	
	
	public ComparisonRowsConfiguration getPassedRowsConfig()
	{
		return passedRowsConfig;
	}
	
	public ComparisonRowsConfiguration getFailedRowsConfig()
	{
		return failedRowsConfig;
	}
	
	public ComparisonRowsConfiguration getNotFoundRowsConfig()
	{
		return notFoundRowsConfig;
	}
	
	public ComparisonRowsConfiguration getExtraRowsConfig()
	{
		return extraRowsConfig;
	}
	
	
	public boolean isFailUnexpectedColumns()
	{
		return failUnexpectedColumns;
	}
}
