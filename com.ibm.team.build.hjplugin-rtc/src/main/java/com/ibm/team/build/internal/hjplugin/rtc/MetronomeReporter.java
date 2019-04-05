/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import com.ibm.team.build.internal.common.helper.TimeFormatHelper;
import com.ibm.team.filesystem.client.internal.MetronomeModel;
import com.ibm.team.repository.client.IStatistics;
import com.ibm.team.repository.client.ITeamRepository;

/** 
 * Service count and elapsed time stats
 *
 */
@SuppressWarnings("restriction")
public class MetronomeReporter {

    private static final String DOT = "."; //$NON-NLS-1$
    private static final String LINE_DELIMITER = System.getProperty("line.separator"); //$NON-NLS-1$
    private static final String SEPARATOR = ","; //$NON-NLS-1$
    private static final String QUOTE = "\""; //$NON-NLS-1$
    
    private static String MetronomeReporter_CALLS="Calls";
    private static String MetronomeReporter_METHOD="Interface/method";
    private static String MetronomeReporter_RETRIES="Retries";
    private static String MetronomeReporter_STATISTICS=": Service Trip Statistics";
    private static String MetronomeReporter_TIME="Time(ms)";
    private static String MetronomeReporter_TIME_AVG="Avg(ms)";
    private static String MetronomeReporter_TIME_ELAPSED="-- Total elapsed time: %s";
    private static String MetronomeReporter_TIME_PERCENT="Time(%)";
    private static String MetronomeReporter_TIME_WORST="Worst(ms)";
    private static String MetronomeReporter_TOTAL_TIME="-- Total time in service calls: %s";
    
	private ITeamRepository fRepo;
	private long fStartTime = 0;
	
	private static class ClassNameComparator implements Comparator<Class<?>> {
		public int compare(Class<?> o1, Class<?> o2) {
			return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
		}
	}
	
	private static class MethodNameComparator implements Comparator<Method> {
		public int compare(Method o1, Method o2) {
			return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
		}
	}
	
	/**
	 * Constructor - initializes reporter to collect statistics
	 * on the calls made to the repository.
	 * 
	 * @param repository The repository to collect access statistics for.
	 */
    public MetronomeReporter(ITeamRepository repository) {
		this.fRepo = repository;
		
		getMetronomeModel().addedRepository(repository);
		// Basically statistics are always collected on the repository
		// its a running count. The MetronomeModel calculates deltas.
		// The reset will capture the current numbers as a baseline to
		// be able to calculate the delta when stats are requested.
		reset();
	}
    
    public static void addTeamRepository(ITeamRepository repo) {
    	getMetronomeModel().addedRepository(repo);
    }

    private static MetronomeModel getMetronomeModel() {
        return MetronomeModel.getInstance();
    }

    /**
	 * Reset the collection of statistics
	 */
	public void reset() {
	    getMetronomeModel().resetServiceMethodStats(this.fRepo.statistics());
	    getMetronomeModel().resetItemTypeStats(this.fRepo.statistics());
		this.fStartTime = System.currentTimeMillis();
	}
	
	/**
	 * Report on the statistics collected so far. This will not reset the 
	 * statistics collection.
	 * 
	 * @param operationLabel A label to prefix the report with
	 * @return A report suitable to put in a log, message, etc. containing the 
	 * service call statistics
	 */
	public String report(String operationLabel) {
		long endTime = System.currentTimeMillis();
		
		StringBuffer report = new StringBuffer();
		report.append(operationLabel + MetronomeReporter_STATISTICS + LINE_DELIMITER);
		
		IStatistics stats = this.fRepo.statistics();
		
		MetronomeModel model = getMetronomeModel();
		
		TreeSet<Class<?>> orderedClasses = new TreeSet<Class<?>>(new ClassNameComparator());
        Class<?>[] classes = model.getServices(stats);
        for (Class<?> c : classes) {
            orderedClasses.add(c);
        }
	
		// The titles for each of the columns to be printed
		List<String> vals = new ArrayList<String>(classes.length * 12);
        Collections.addAll(vals, MetronomeReporter_METHOD, MetronomeReporter_CALLS,
        		MetronomeReporter_TIME, MetronomeReporter_TIME_PERCENT,
        		MetronomeReporter_TIME_AVG,
        		MetronomeReporter_TIME_WORST, MetronomeReporter_RETRIES);
        
        int maxCol1width = MetronomeReporter_METHOD.length();

		long totalElapsedTime = model.getTotalElapsedTime(stats);
		
		// Report for the service overall and then for each of the methods within that were called
		for (Class<?> service : orderedClasses) {
			String serviceFullName = service.getName();
			String serviceName =
				serviceFullName.substring(serviceFullName.lastIndexOf('.') + 1);
			
			long serviceCallCount = model.getServiceCallCount(stats, service);
			long serviceElapsedTime = model.getServiceElapsedTime(stats, service);
			long servicePercentage = Math.round(serviceElapsedTime / (double) totalElapsedTime * 100.0);
			double serviceAverage = Math.round(100.0 * serviceElapsedTime/(double)serviceCallCount) / 100.0;
			
			Collections.addAll(vals, "  " + serviceName, //$NON-NLS-1$
					Long.toString(serviceCallCount),
					Long.toString(serviceElapsedTime),
					Long.toString(servicePercentage), 
					Double.toString(serviceAverage),
					Long.toString(model.getServiceWorstTime(stats, service)),
					Long.toString(model.getServiceRetryCount(stats, service)));
			
			maxCol1width = Math.max(maxCol1width, 2 + serviceName.length());
			
			// Display the methods
			TreeSet<Method> orderedMethods = new TreeSet<Method>(new MethodNameComparator());
			Method[] methods = model.getMethods(stats, service);
			for (Method m : methods) {
			    orderedMethods.add(m);
			}
			for (Method serviceMethod : orderedMethods) {
				long methodCallCount = model.getMethodCallCount(stats, service, serviceMethod);
				long methodElapsedTime = model.getMethodElapsedTime(stats, service, serviceMethod);
				long methodPercentage = Math.round(methodElapsedTime / (double) totalElapsedTime * 100.0);
				double methodAverage = Math.round(100.0 * methodElapsedTime/(double)methodCallCount) / 100.0;
				long methodWorstTime = model.getMethodWorstTime(stats, service, serviceMethod);
				long methodRetryCount = model.getMethodRetryCount(stats, service, serviceMethod);
				
				Collections.addAll(vals,
						"    " + serviceMethod.getName(),  //$NON-NLS-1$
						Long.toString(methodCallCount),
						Long.toString(methodElapsedTime),
						Long.toString(methodPercentage),
						Double.toString(methodAverage),
						Long.toString(methodWorstTime),
						Long.toString(methodRetryCount));
				maxCol1width = Math.max(maxCol1width, 4 + serviceMethod.getName().length());
			}
		}
		
		// now that we know the maximum size of the method column, print out the info collected
		printStats(report, vals.toArray(new String[vals.size()]), new int[] {maxCol1width,
		    Math.max(15, MetronomeReporter_CALLS.length()),
		    Math.max(15, MetronomeReporter_TIME.length()),
		    Math.max(10, MetronomeReporter_TIME_AVG.length()),
		    Math.max(15, MetronomeReporter_TIME_PERCENT.length()),
		    Math.max(15, MetronomeReporter_TIME_WORST.length()),
		    Math.max(15, MetronomeReporter_RETRIES.length())});
		
		// Also include the overall info
		report.append(String.format(MetronomeReporter_TOTAL_TIME, TimeFormatHelper.formatAbbreviatedTime(totalElapsedTime, true)) + LINE_DELIMITER);
		report.append(String.format(MetronomeReporter_TIME_ELAPSED, TimeFormatHelper.formatAbbreviatedTime((endTime - this.fStartTime), true)) + LINE_DELIMITER);
		return report.toString();
	}

	private static void printStats(StringBuffer report, String[] stats, int[] columnWidths) {
		int statsLength = stats.length;
		
		for (int i = 0; i < statsLength;) {
			StringBuilder line = new StringBuilder();
			boolean padRight = true;
			for (int column = 0; column < columnWidths.length; column++) {
				append(line, stats[i], columnWidths[column], padRight);
				i++;
				padRight = false;
			}
			report.append(line.toString()).append(LINE_DELIMITER);
		}
	}

	private static void append(StringBuilder line, String columnData, int width, boolean padRight) {
		if (padRight) {
			line.append(columnData);
		}
		for (int i = columnData.length(); i < width; i++) {
			line.append(" ");	//$NON-NLS-1$
		}
		if (!padRight) {
			line.append(columnData);
		}
	}
    
    /**
     * Report on the statistics collected so far in CSV format. This will not reset the 
     * statistics collection. Total time in Service calls and total elapse time are not
     * reported.
     * 
     * @return A report in CSV format (comma separated values) containing the 
     * service call statistics
     */
    public String reportCSVFormat() {
        StringBuffer report = new StringBuffer();
        IStatistics stats = this.fRepo.statistics();
        
        MetronomeModel model = getMetronomeModel();
        
        TreeSet<Class<?>> orderedClasses = new TreeSet<Class<?>>(new ClassNameComparator());
        Class<?>[] classes = model.getServices(stats);
        for (Class<?> c : classes) {
            orderedClasses.add(c);
        }
    
        // First row is all the titles
        report.append(QUOTE).append(MetronomeReporter_METHOD).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_CALLS).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_TIME).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_TIME_PERCENT).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_TIME_AVG).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_TIME_WORST).append(QUOTE).append(SEPARATOR);
        report.append(QUOTE).append(MetronomeReporter_RETRIES).append(QUOTE).append(SEPARATOR);
        report.append(LINE_DELIMITER);
        
        long totalElapsedTime = model.getTotalElapsedTime(stats);
        
        // Report the time for the service overall and then the methods within it
        // one line for each method (and one line for the service)
        for (Class<?> service : orderedClasses) {
            String serviceFullName = service.getName();
            String serviceName =
                serviceFullName.substring(serviceFullName.lastIndexOf('.') + 1);
            
            long serviceCallCount = model.getServiceCallCount(stats, service);
            long serviceElapsedTime = model.getServiceElapsedTime(stats, service);
            long servicePercentage = Math.round(serviceElapsedTime / (double) totalElapsedTime * 100.0);
            double serviceAverage = Math.round(100.0 * serviceElapsedTime/(double)serviceCallCount) / 100.0;
            
            report.append(QUOTE).append(serviceName).append(QUOTE).append(SEPARATOR);
            report.append(Long.toString(serviceCallCount)).append(SEPARATOR);
            report.append(Long.toString(serviceElapsedTime)).append(SEPARATOR);
            report.append(Long.toString(servicePercentage)).append(SEPARATOR); 
            report.append(Double.toString(serviceAverage)).append(SEPARATOR);
            report.append(Long.toString(model.getServiceWorstTime(stats, service))).append(SEPARATOR);
            report.append(Long.toString(model.getServiceRetryCount(stats, service))).append(LINE_DELIMITER);
            
            // Report the methods
            TreeSet<Method> orderedMethods = new TreeSet<Method>(new MethodNameComparator());
            Method[] methods = model.getMethods(stats, service);
            for (Method m : methods) {
                orderedMethods.add(m);
            }
            for (Method serviceMethod : orderedMethods) {
                long methodCallCount = model.getMethodCallCount(stats, service, serviceMethod);
                long methodElapsedTime = model.getMethodElapsedTime(stats, service, serviceMethod);
                long methodPercentage = Math.round(methodElapsedTime / (double) totalElapsedTime * 100.0);
                double methodAverage = Math.round(100.0 * methodElapsedTime/(double)methodCallCount) / 100.0;

                report.append(QUOTE).append(serviceName).append(DOT).append(serviceMethod.getName()).append(QUOTE).append(
                        SEPARATOR);
                report.append(Long.toString(methodCallCount)).append(SEPARATOR);
                report.append(Long.toString(methodElapsedTime)).append(SEPARATOR);
                report.append(Long.toString(methodPercentage)).append(SEPARATOR);
                report.append(Double.toString(methodAverage)).append(SEPARATOR);
                report.append(Long.toString(model.getMethodWorstTime(stats, service, serviceMethod))).append(
                        SEPARATOR);
                report.append(Long.toString(model.getMethodRetryCount(stats, service, serviceMethod))).append(
                        LINE_DELIMITER);
            }
        }
        return report.toString();
    }
}
