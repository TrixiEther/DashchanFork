package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.util.Pair;
import chan.content.Chan;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;

public class CaptchaSolving {
	private static final CaptchaSolving INSTANCE = new CaptchaSolving();

	public static CaptchaSolving getInstance() {
		return INSTANCE;
	}

	private enum ServiceType {ANTIGATE_LEGACY}

	public static class UnsupportedServiceException extends Exception {}
	public static class InvalidTokenException extends Exception {}

	private Uri createUri(String endpoint) {
		Uri uri = Uri.parse(endpoint);
		if (StringUtils.isEmpty(uri.getScheme())) {
			uri = uri.buildUpon().scheme(Chan.getFallback().locator.isUseHttps() ? "https" : "http").build();
		}
		return uri;
	}

	private Pair<String, String> getConfiguration() {
		Map<String, String> map = Preferences.getCaptchaSolving();
		if (map == null) {
			return null;
		}
		String endpoint = map.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_ENDPOINT);
		String token = map.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_TOKEN);
		if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(token)) {
			return null;
		}
		return new Pair<>(endpoint, token);
	}

	public boolean hasConfiguration() {
		return getConfiguration() != null;
	}

	public Map<String, String> checkService(HttpHolder holder) throws HttpException,
			UnsupportedServiceException, InvalidTokenException {
		Pair<String, String> configuration = getConfiguration();
		if (configuration == null) {
			throw new UnsupportedServiceException();
		}
		String endpoint = configuration.first;
		String token = configuration.second;
		LinkedHashMap<String, String> extra = new LinkedHashMap<>();
		try {
			checkServiceInternal(holder, endpoint, token, extra);
		} catch (UnsupportedServiceException e) {
			// Check for HTTP exception
			new HttpRequest(createUri(endpoint), holder).setHeadMethod()
					.setSuccessOnly(false).perform().cleanupAndDisconnect();
			throw e;
		}
		return extra;
	}

	private ServiceType checkServiceInternal(HttpHolder holder, String endpoint, String token,
			Map<String, String> outExtra) throws HttpException, UnsupportedServiceException, InvalidTokenException {
		ServiceType serviceType = checkServiceType(holder, endpoint);
		if (serviceType == null) {
			throw new UnsupportedServiceException();
		}
		if (outExtra != null) {
			outExtra.put("protocol", serviceType.name());
		}
		if (!checkServiceAuthorization(holder, serviceType, endpoint, token, outExtra)) {
			throw new InvalidTokenException();
		}
		return serviceType;
	}

	private Pair<String, ServiceType> lastServiceType;

	private ServiceType checkServiceType(HttpHolder holder, String endpoint) throws HttpException {
		synchronized (this) {
			if (lastServiceType != null && endpoint.equals(lastServiceType.first)) {
				return lastServiceType.second;
			}
		}
		Uri endpointUri = createUri(endpoint);
		ServiceType serviceType = null;
		try {
			Uri uri = endpointUri.buildUpon().appendPath("res.php")
					.appendQueryParameter("key", "")
					.appendQueryParameter("action", "getbalance")
					.build();
			String response = new HttpRequest(uri, holder).setSuccessOnly(false).perform().readString();
			if (response != null && (response.startsWith("OK|") ||
					response.startsWith("ERROR_") && response.contains("KEY"))) {
				serviceType = ServiceType.ANTIGATE_LEGACY;
			}
		} catch (HttpException e) {
			if (!e.isHttpException() && !e.isSocketException()) {
				throw e;
			}
		}
		if (serviceType != null) {
			synchronized (this) {
				lastServiceType = new Pair<>(endpoint, serviceType);
			}
			return serviceType;
		}
		return null;
	}

	private Pair<String, String> lastAuthorization;

	private boolean checkServiceAuthorization(HttpHolder holder, ServiceType serviceType,
			String endpoint, String token, Map<String, String> outExtra) throws HttpException {
		Uri endpointUri = createUri(endpoint);
		Pair<String, String> authorization = new Pair<>(endpoint, token);
		boolean success;
		switch (serviceType) {
			case ANTIGATE_LEGACY: {
				if (outExtra == null) {
					synchronized (this) {
						if (authorization.equals(lastAuthorization)) {
							return true;
						}
					}
				}
				Uri uri = endpointUri.buildUpon().appendPath("res.php")
						.appendQueryParameter("key", token)
						.appendQueryParameter("action", "getbalance")
						.build();
				String response = new HttpRequest(uri, holder).setSuccessOnly(true).perform().readString();
				if (response != null && response.startsWith("OK|")) {
					if (outExtra != null) {
						outExtra.put("balance", response.substring(3));
					}
					success = true;
				} else if (response != null && response.startsWith("ERROR_") && response.contains("KEY")) {
					success = false;
				} else {
					try {
						Float.parseFloat(StringUtils.emptyIfNull(response));
						if (outExtra != null) {
							outExtra.put("balance", response);
						}
						success = true;
					} catch (NumberFormatException e) {
						throw new HttpException(ErrorItem.Type.INVALID_RESPONSE,
								true, false, new Exception(response));
					}
				}
				break;
			}
			default: {
				throw new IllegalStateException();
			}
		}
		if (success) {
			synchronized (this) {
				lastAuthorization = authorization;
			}
		}
		return success;
	}

	private void waitOrThrow(int ms) throws HttpException {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	public enum CaptchaType {RECAPTCHA_2, RECAPTCHA_2_INVISIBLE, HCAPTCHA}

	public String solveCaptcha(HttpHolder holder, CaptchaType captchaType,
			String apiKey, String referer) throws HttpException {
		try {
			Pair<String, String> configuration = getConfiguration();
			if (configuration == null) {
				return null;
			}
			String endpoint = configuration.first;
			String token = configuration.second;
			ServiceType serviceType;
			try {
				serviceType = checkServiceInternal(holder, endpoint, token, null);
			} catch (UnsupportedServiceException | InvalidTokenException e) {
				return null;
			}
			Uri endpointUri = createUri(endpoint);
			switch (serviceType) {
				case ANTIGATE_LEGACY: {
					Uri.Builder builder = endpointUri.buildUpon().appendPath("in.php");
					builder.appendQueryParameter("key", token);
					switch (captchaType) {
						case RECAPTCHA_2: {
							builder.appendQueryParameter("method", "userrecaptcha");
							builder.appendQueryParameter("googlekey", apiKey);
							builder.appendQueryParameter("invisible", "0");
							break;
						}
						case RECAPTCHA_2_INVISIBLE: {
							builder.appendQueryParameter("method", "userrecaptcha");
							builder.appendQueryParameter("googlekey", apiKey);
							builder.appendQueryParameter("invisible", "1");
							break;
						}
						case HCAPTCHA: {
							builder.appendQueryParameter("method", "hcaptcha");
							builder.appendQueryParameter("sitekey", apiKey);
							break;
						}
					}
					builder.appendQueryParameter("pageurl", referer);
					String response = new HttpRequest(builder.build(), holder).perform().readString();
					if (response != null && response.startsWith("OK|")) {
						response = response.substring(3);
					} else {
						throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, true, false, new Exception(response));
					}
					Uri uri = endpointUri.buildUpon().appendPath("res.php")
							.appendQueryParameter("key", token)
							.appendQueryParameter("action", "get")
							.appendQueryParameter("id", response)
							.build();
					int wait = 0;
					while (true) {
						if (wait < 5) {
							wait++;
						}
						waitOrThrow(wait * 1000);
						response = new HttpRequest(uri, holder).perform().readString();
						if (response != null && response.startsWith("OK|")) {
							return response.substring(3);
						} else if (!"CAPCHA_NOT_READY".equals(response)) {
							throw new HttpException(ErrorItem.Type.INVALID_RESPONSE, true, false,
									new Exception(response));
						}
					}
				}
				default: {
					throw new IllegalStateException();
				}
			}
		} catch (HttpException e) {
			if (e.isHttpException() || e.isSocketException()) {
				Log.persistent().stack(e);
			} else {
				throw e;
			}
		}
		return null;
	}
}