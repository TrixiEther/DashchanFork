package chan.http;

import android.os.Parcel;
import android.os.Parcelable;
import chan.annotation.Public;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

@Public
public final class HttpValidator implements Parcelable {
	private final String entityTag;
	private final String lastModified;

	public HttpValidator(String entityTag, String lastModified) {
		this.entityTag = entityTag;
		this.lastModified = lastModified;
	}

	static HttpValidator obtain(HttpURLConnection connection) {
		String eTag = connection.getHeaderField("ETag");
		String lastModified = connection.getHeaderField("Last-Modified");
		if (eTag != null || lastModified != null) {
			return new HttpValidator(eTag, lastModified);
		}
		return null;
	}

	public void write(HttpURLConnection connection) {
		if (!StringUtils.isEmpty(entityTag)) {
			connection.setRequestProperty("If-None-Match", entityTag);
		}
		if (!StringUtils.isEmpty(lastModified)) {
			connection.setRequestProperty("If-Modified-Since", lastModified);
		}
	}

	@Override
	public String toString() {
		JSONObject jsonObject = new JSONObject();
		try {
			if (!StringUtils.isEmpty(entityTag)) {
				jsonObject.put("ETag", entityTag);
			}
			if (!StringUtils.isEmpty(lastModified)) {
				jsonObject.put("LastModified", lastModified);
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return jsonObject.toString();
	}

	public static HttpValidator fromString(String validator) {
		if (validator != null) {
			try {
				JSONObject jsonObject = new JSONObject(validator);
				String eTag = CommonUtils.optJsonString(jsonObject, "ETag");
				String lastModified = CommonUtils.optJsonString(jsonObject, "LastModified");
				if (eTag != null || lastModified != null) {
					return new HttpValidator(eTag, lastModified);
				}
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		return null;
	}

	public void serialize(JsonSerial.Writer writer) throws IOException {
		writer.startObject();
		if (entityTag != null) {
			writer.name("entityTag");
			writer.value(entityTag);
		}
		if (lastModified != null) {
			writer.name("lastModified");
			writer.value(lastModified);
		}
		writer.endObject();
	}

	public static HttpValidator deserialize(JsonSerial.Reader reader) throws IOException, ParseException {
		String entityTag = null;
		String lastModified = null;
		reader.startObject();
		while (!reader.endStruct()) {
			switch (reader.nextName()) {
				case "entityTag": {
					entityTag = reader.nextString();
					break;
				}
				case "lastModified": {
					lastModified = reader.nextString();
					break;
				}
				default: {
					reader.skip();
					break;
				}
			}
		}
		return new HttpValidator(entityTag, lastModified);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(entityTag);
		dest.writeString(lastModified);
	}

	public static final Creator<HttpValidator> CREATOR = new Creator<HttpValidator>() {
		@Override
		public HttpValidator createFromParcel(Parcel source) {
			String entityTag = source.readString();
			String lastModified = source.readString();
			return new HttpValidator(entityTag, lastModified);
		}

		@Override
		public HttpValidator[] newArray(int size) {
			return new HttpValidator[size];
		}
	};
}
