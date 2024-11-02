package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Arrays;
import java.util.function.Consumer;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import tukano.api.Result;
import utils.Hash;

public class CloudSystemStorage implements BlobStorage {

	private static final String BLOBS_CONTAINER_NAME = "blobs";

	private final String storageConnectionString;
	private final BlobContainerClient containerClient;
	private static final int CHUNK_SIZE = 4096;

	public CloudSystemStorage() {

		Dotenv dotenv = Dotenv.configure().load();

		// Get connection string in the storage access keys page
		this.storageConnectionString = dotenv.get("STORAGE_CONNECTION_STRING");

		// Get container client
		this.containerClient = new BlobContainerClientBuilder()
				.connectionString(storageConnectionString)
				.containerName(BLOBS_CONTAINER_NAME)
				.buildClient();
	}

	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);

		BinaryData data = BinaryData.fromBytes(bytes);

		BlobClient blob = containerClient.getBlobClient(path);

		// If the blob already exists, check if the content is the same
		if (blob.exists()) {
			byte[] existingBytes = blob.downloadContent().toBytes();
			if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(existingBytes))) {
				return ok(); // same file data
			} else {
				return error(CONFLICT); // Conflict because content differs
			}
		}

		blob.upload(data);

		return ok();
	}

	@Override
	public Result<byte[]> read(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		BlobClient blob = containerClient.getBlobClient(path);

		if (!blob.exists())
			return error(NOT_FOUND);

		BinaryData data = blob.downloadContent();

		byte[] bytes = data.toBytes();

		return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null) {
			return error(BAD_REQUEST);
		}

		try {
			// Get BlobClient for the specific blob
			BlobClient blobClient = containerClient.getBlobClient(path);

			// Check if blob exists
			if (!blobClient.exists()) {
				return error(NOT_FOUND);
			}

			// Use a ByteArrayOutputStream to capture the downloaded blob data
			try (var outputStream = new java.io.ByteArrayOutputStream()) {
				// Download the blob content into the output stream
				blobClient.downloadStream(outputStream);

				// Convert the output stream to a byte array
				byte[] blobBytes = outputStream.toByteArray();

				// Process the byte array in chunks
				int offset = 0;
				while (offset < blobBytes.length) {
					// Determine the chunk size (remaining bytes or CHUNK_SIZE)
					int chunkSize = Math.min(CHUNK_SIZE, blobBytes.length - offset);
					byte[] chunk = Arrays.copyOfRange(blobBytes, offset, offset + chunkSize);

					// Pass each chunk to the consumer
					sink.accept(chunk);

					// Update the offset for the next chunk
					offset += chunkSize;
				}
			}

			return ok();
		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> delete(String path) {
		if (path == null) {
			return error(BAD_REQUEST);
		}

		try {
			// Get BlobClient for the specific blob
			BlobClient blobClient = containerClient.getBlobClient(path);

			// Check if blob exists
			if (!blobClient.exists()) {
				return error(NOT_FOUND);
			}

			// Delete the blob
			blobClient.delete();

			return ok();

		} catch (Exception e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
	}

}