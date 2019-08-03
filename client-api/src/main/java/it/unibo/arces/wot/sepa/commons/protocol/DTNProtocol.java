package it.unibo.arces.wot.sepa.commons.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.http.HttpStatus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.pattern.JSAP;
import it.unibo.dtn.JAL.BPSocket;
import it.unibo.dtn.JAL.Bundle;
import it.unibo.dtn.JAL.BundleEID;
import it.unibo.dtn.JAL.BundleEIDDTNScheme;
import it.unibo.dtn.JAL.BundleEIDIPNScheme;
import it.unibo.dtn.JAL.BundleTimestamp;
import it.unibo.dtn.JAL.exceptions.JALIPNParametersException;
import it.unibo.dtn.JAL.exceptions.JALLocalEIDException;
import it.unibo.dtn.JAL.exceptions.JALNotRegisteredException;
import it.unibo.dtn.JAL.exceptions.JALNullPointerException;
import it.unibo.dtn.JAL.exceptions.JALOpenException;
import it.unibo.dtn.JAL.exceptions.JALReceiveException;
import it.unibo.dtn.JAL.exceptions.JALReceptionInterruptedException;
import it.unibo.dtn.JAL.exceptions.JALRegisterException;
import it.unibo.dtn.JAL.exceptions.JALSendException;
import it.unibo.dtn.JAL.exceptions.JALTimeoutException;
import it.unibo.dtn.JAL.exceptions.JALUnregisterException;

public class DTNProtocol implements ISPARQL11Interface {
	private static final String DEMUXDTN = "/client";
	private static final int BASEDEMUXIPN = 20000;
	private static final int SIZERANDOMDEMUXIPN = 1000;
	
	private BPSocket socket;
	
	public DTNProtocol() throws JALLocalEIDException, JALOpenException, JALIPNParametersException, JALRegisterException {
		final int demuxIPN = new Random(System.currentTimeMillis()).nextInt(BASEDEMUXIPN) + SIZERANDOMDEMUXIPN;
		final String demuxDTN = DEMUXDTN + '_' + demuxIPN;
		this.socket = BPSocket.register(demuxDTN, demuxIPN);
	}
	
	@Override
	public Response query(QueryRequest req) {
		final BundleEID destination;
		
		String schema = req.getScheme();
		if (schema.equals("ipn")) {
			try {
				destination = new BundleEIDIPNScheme(Integer.parseInt(req.getHost()), req.getPort());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Error on host. Must be an integer");
			}
		} else if (schema.equals("dtn")) {
			destination = new BundleEIDDTNScheme(req.getHost(), req.getPath());
		} else {
			throw new IllegalArgumentException("No schema found for DTN protocol.");
		}
		
		Bundle bundle = new Bundle(destination);
		String queryString = req.getSPARQL();
		final byte[] queryData = queryString.getBytes(StandardCharsets.UTF_8);
		
		bundle.setData(queryData);
		
		try {
			this.socket.send(bundle);
		} catch (NullPointerException | IllegalArgumentException | IllegalStateException | JALNullPointerException
				| JALNotRegisteredException | JALSendException e) {
			return new ErrorResponse(999, "sending_bundle", "Error on sending bundle. " + e.getMessage());
		}
		
		boolean received = false;
		while (!received) {
			try {
				bundle = this.socket.receive((int) req.getTimeout());
				received = true;
			} catch (JALReceptionInterruptedException e) {
				continue;
			}
			catch (JALTimeoutException e) {
				return new ErrorResponse(504, "timeout", "Timeout on receiving DTN bundle. " + e.getMessage());
			}
			catch (JALNotRegisteredException | JALReceiveException e) {
				return new ErrorResponse(999, "receiving_bundle", "Error on receiving bundle. " + e.getMessage());
			}
		}
		
		
		ByteBuffer buffer = ByteBuffer.wrap(bundle.getData());
		DtnResponseHeader responseHeader = DtnResponseHeader.read(buffer);
		
		if (responseHeader.getResultCode() >= 400) {
			return new ErrorResponse(responseHeader.getResultCode(), responseHeader.getMessage(), responseHeader.getErrorDescription());
		} else {
			byte[] array = new byte[buffer.remaining()];
			int i = 0;
			while (buffer.hasRemaining()) {
				array[i] = buffer.get();
				i++;
			}
			String responseString = new String(array, StandardCharsets.UTF_8);
			
			JsonObject ret = null;
			try {
				ret = new JsonParser().parse(responseString).getAsJsonObject();
			} catch (JsonParseException e) {
				return new ErrorResponse(HttpStatus.SC_UNPROCESSABLE_ENTITY, "JsonParsingException",
						e.getMessage() + " Response body: " + responseString);
			}
			return new QueryResponse(ret);
		}
	}

	@Override
	public Response update(UpdateRequest req) {
		final BundleEID destination;
		
		String schema = req.getScheme();
		if (schema.equals("ipn")) {
			try {
				destination = new BundleEIDIPNScheme(Integer.parseInt(req.getHost()), req.getPort());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Error on host. Must be an integer");
			}
		} else if (schema.equals("dtn")) {
			destination = new BundleEIDDTNScheme(req.getHost(), req.getPath());
		} else {
			throw new IllegalArgumentException("No schema found for DTN protocol.");
		}
		
		Bundle bundle = new Bundle(destination);
		String queryString = req.getSPARQL();
		final byte[] queryData = queryString.getBytes(StandardCharsets.UTF_8);
		
		bundle.setData(queryData);
		
		try {
			this.socket.send(bundle);
		} catch (NullPointerException | IllegalArgumentException | IllegalStateException | JALNullPointerException
				| JALNotRegisteredException | JALSendException e) {
			return new ErrorResponse(999, "sending_bundle", "Error on sending bundle. " + e.getMessage());
		}
		
		boolean received = false;
		while (!received) {
			try {
				final int timeout = (req.getTimeout() <= 0 ? -1 : (int)req.getTimeout()); // timeout <= 0 --> blocking receive
				bundle = this.socket.receive(timeout);
				received = true;
			} catch (JALReceptionInterruptedException e) {
				continue;
			}
			catch (JALTimeoutException e) {
				return new ErrorResponse(504, "timeout", "Timeout on receiving DTN bundle. " + e.getMessage());
			}
			catch (JALNotRegisteredException | JALReceiveException e) {
				return new ErrorResponse(999, "receiving_bundle", "Error on receiving bundle. " + e.getMessage());
			}
		}
		
		
		ByteBuffer buffer = ByteBuffer.wrap(bundle.getData());
		DtnResponseHeader responseHeader = DtnResponseHeader.read(buffer);
		
		if (responseHeader.getResultCode() >= 400) {
			return new ErrorResponse(responseHeader.getResultCode(), responseHeader.getMessage(), responseHeader.getErrorDescription());
		} else {
			byte[] array = new byte[buffer.remaining()];
			int i = 0;
			while (buffer.hasRemaining()) {
				array[i] = buffer.get();
				i++;
			}
			String responseString = new String(array, StandardCharsets.UTF_8);
			
			return new UpdateResponse(responseString);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			this.socket.unregister();
		} catch (JALUnregisterException e) {
			
		}
	}
	
	public static DTNProtocol of(JSAP appProfile) {
		return null;
	}



	// TODO to synchronize with the class in engine project (used inside SEPA)
	private static class DtnResponseHeader {
		@SuppressWarnings("unused")
		private BundleTimestamp timestamp;
		private String message;
		private int resultCode;
		private String errorDescription;
		
		/**
		 * Creates a reponse header
		 * @param timestamp the timestamp of bundle (used to identify uniquely the bundle)
		 */
		private DtnResponseHeader(BundleTimestamp timestamp, String message, int resultCode, String errorDescription) {
			this.timestamp = timestamp;
			this.resultCode = resultCode;
			this.message = message;
			this.errorDescription = errorDescription;
		}
		
		public String getMessage() {
			return message;
		}

		public int getResultCode() {
			return resultCode;
		}

		public String getErrorDescription() {
			return errorDescription;
		}

		public static DtnResponseHeader read(ByteBuffer buffer) {
			BundleTimestamp timestamp = new BundleTimestamp(buffer.getInt(), buffer.getInt());
			
			int size = buffer.getInt();
			byte[] byteArray = new byte[size];
			for (int i = 0; i < size; i++) {
				byteArray[i] = buffer.get();
			}
			String message = new String(byteArray, StandardCharsets.UTF_8);
			
			final int resultCode = buffer.getInt();
			
			size = buffer.getInt();
			byteArray = new byte[size];
			for (int i = 0; i < size; i++) {
				byteArray[i] = buffer.get();
			}
			String descrpiton = new String(byteArray, StandardCharsets.UTF_8);
			
			return new DtnResponseHeader(timestamp, message, resultCode, descrpiton);
		}
		
	}

}