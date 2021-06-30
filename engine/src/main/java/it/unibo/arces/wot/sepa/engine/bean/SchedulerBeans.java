/* This class belongs to the JMX classes used for the remote monitoring of the engine
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.unibo.arces.wot.sepa.engine.bean;

import it.unibo.arces.wot.sepa.engine.scheduling.InternalRequest;

public class SchedulerBeans {

	private static long outOfTokenRequests = 0;
	private static long maxPendingsRequests = 0;
	private static long pendingRequests = 0;
	
	private static long scheduledRequests = 0;
	private static long totalUpdateRequests = 0;
	private static long totalQueryRequests = 0;
	private static long totalSubscribeRequests = 0;
	private static long totalUnsubscribeRequests = 0;
	
	private static int queueSize = 100;

	public static long getQueue_Pending() {
		return pendingRequests;
	}

	public static long getQueue_Max() {
		return maxPendingsRequests;
	}

	public static long getQueue_OutOfToken() {
		return outOfTokenRequests;
	}

	public static String getStatistics() {
		return String.format(
				"Updates %d Queries %d Subscribes %d Unsubscribes %d",
				totalUpdateRequests, totalQueryRequests, totalSubscribeRequests, totalUnsubscribeRequests);
	}

	public static void reset() {
		 outOfTokenRequests = 0;
		 maxPendingsRequests = 0;
		 pendingRequests = 0;

		 scheduledRequests = 0;
		 totalUpdateRequests = 0;
		 totalQueryRequests = 0;
		 totalSubscribeRequests = 0;
		 totalUnsubscribeRequests = 0;
		 
		 queueSize = 100;
	}

	public static void tokenLeft(int size) {
		pendingRequests =  queueSize - size;
		if (pendingRequests > maxPendingsRequests)
			maxPendingsRequests = pendingRequests;

	}

	public static void newRequest(InternalRequest req,boolean scheduled) {	
		if (scheduled)
			scheduledRequests++;
		else
			outOfTokenRequests++;
		
		if (req.isUpdateRequest()) totalUpdateRequests++;
		else if (req.isQueryRequest()) totalQueryRequests++;
		else if (req.isSubscribeRequest()) totalSubscribeRequests++;
		else if (req.isUnsubscribeRequest()) totalUnsubscribeRequests++;
	}

	public static long getScheduledRequests() {
		return scheduledRequests;
	}

	public static void setQueueSize(int schedulingQueueSize) {
		queueSize = schedulingQueueSize;	
	}
	
	public static int getQueueSize() {
		return queueSize;	
	}
}
