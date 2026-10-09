package logisticspipes.proxy.cc.wrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaFunction;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;

import logisticspipes.LogisticsPipes;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCDirectCall;
import logisticspipes.proxy.computers.interfaces.CCQueued;
import logisticspipes.proxy.computers.interfaces.ICCTypeWrapped;
import logisticspipes.proxy.computers.wrapper.CCObjectWrapper;
import logisticspipes.proxy.computers.wrapper.CCWrapperInformation;
import logisticspipes.proxy.computers.wrapper.ICommandWrapper;
import logisticspipes.security.PermissionException;
import logisticspipes.ticks.QueuedTasks;

public class CCCommandWrapper {

	public static final ICommandWrapper WRAPPER = CCCommandWrapper::new;

	// table key that lets a returned object be passed back as an argument
	public static final String HANDLE_KEY = "lpHandle";

	private static final AtomicLong NEXT_HANDLE = new AtomicLong(1);
	// weak values: an entry lives as long as a Lua table still holds the wrapper's functions
	private static final Cache<Long, CCCommandWrapper> HANDLES = CacheBuilder.newBuilder().weakValues().build();

	private final CCWrapperInformation info;
	private final Object object;
	private final long handle;
	private final String[] methodNames;
	private Map<Object, Object> table;

	public CCCommandWrapper(CCWrapperInformation info, Object object) {
		this.info = info;
		this.object = object;
		handle = NEXT_HANDLE.getAndIncrement();
		HANDLES.put(handle, this);
		List<String> names = new ArrayList<>();
		names.add("help");
		names.add("commandHelp");
		names.add("getType");
		for (int i = 0; i < info.commandMap.size(); i++) {
			names.add(info.commandMap.get(i));
		}
		methodNames = names.toArray(new String[0]);
	}

	public String[] getMethodNames() {
		return methodNames;
	}

	public String getType() {
		return info.type;
	}

	public Object getObject() {
		return object;
	}

	private synchronized Map<Object, Object> asTable() {
		if (table == null) {
			Map<Object, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < methodNames.length; i++) {
				final int methodId = i;
				map.putIfAbsent(methodNames[i], (ILuaFunction) args -> callMethod(methodId, args, false));
			}
			map.put(HANDLE_KEY, (double) handle);
			table = map;
		}
		return table;
	}

	public MethodResult callMethod(int methodId, IArguments arguments, boolean directCall) throws LuaException {
		Object[] args = fromLua(arguments.getAll());
		if (methodId == 0) {
			return MethodResult.of(help(args));
		}
		if (methodId == 1) {
			return MethodResult.of(helpCommand(args));
		}
		if (methodId == 2) {
			return MethodResult.of(info.type);
		}
		String name = methodNames[methodId];
		Method match = null;
		for (Method method : info.commands.values()) {
			if (method.getName().equalsIgnoreCase(name) && argumentsMatch(method, args)) {
				match = method;
				break;
			}
		}
		if (match == null) {
			throw new LuaException(noSuchMethod(name));
		}
		if (match.getAnnotation(CCDirectCall.class) != null && !directCall) {
			throw new LuaException("Permission denied");
		}
		if (match.getAnnotation(CCCommand.class).needPermission() && info.securityMethod != null) {
			invoke(info.securityMethod, new Object[0]);
		}
		Object result;
		if (match.getAnnotation(CCQueued.class) != null) {
			result = invokeOnServerThread(match, args);
		} else {
			result = invoke(match, args);
		}
		return MethodResult.of(toLua(CCObjectWrapper.createArray(CCObjectWrapper.getWrappedObject(result, WRAPPER))));
	}

	private Object invoke(Method method, Object[] args) throws LuaException {
		try {
			return method.invoke(object, args);
		} catch (InvocationTargetException e) {
			throw asLuaException(e.getTargetException());
		} catch (IllegalAccessException | IllegalArgumentException e) {
			throw asLuaException(e);
		}
	}

	private Object invokeOnServerThread(Method method, Object[] args) throws LuaException {
		CompletableFuture<Object> future = new CompletableFuture<>();
		QueuedTasks.queueTask(() -> {
			try {
				future.complete(method.invoke(object, args));
			} catch (InvocationTargetException e) {
				future.completeExceptionally(e.getTargetException());
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
			return null;
		});
		try {
			return future.get(2, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			LogisticsPipes.log.warn("CC call " + method.getName() + " on " + object.getClass().getName() + " (" + object + ") took too long.");
			throw new LuaException("Took too long");
		} catch (ExecutionException e) {
			throw asLuaException(e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LuaException("Interrupted");
		}
	}

	private static LuaException asLuaException(Throwable t) {
		if (t instanceof LuaException) {
			return (LuaException) t;
		}
		if (t instanceof PermissionException) {
			return new LuaException("Permission denied");
		}
		return new LuaException(t.getMessage() != null ? t.getMessage() : t.toString());
	}

	public static Object[] toLuaArguments(Object[] arguments) {
		return toLua(CCObjectWrapper.createArray(CCObjectWrapper.getWrappedObject(arguments, WRAPPER)));
	}

	// wrapped objects become tables; nested arrays and maps are converted in place
	private static Object[] toLua(Object[] values) {
		if (values == null) {
			return new Object[0];
		}
		for (int i = 0; i < values.length; i++) {
			values[i] = toLuaValue(values[i]);
		}
		return values;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object toLuaValue(Object value) {
		if (value instanceof CCCommandWrapper) {
			return ((CCCommandWrapper) value).asTable();
		}
		if (value instanceof Object[]) {
			return toLua((Object[]) value);
		}
		if (value instanceof Map) {
			Map map = new HashMap();
			((Map<?, ?>) value).forEach((k, v) -> map.put(toLuaValue(k), toLuaValue(v)));
			return map;
		}
		return value;
	}

	private static Object[] fromLua(Object[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof Map && ((Map<?, ?>) args[i]).get(HANDLE_KEY) instanceof Number) {
				CCCommandWrapper wrapper = HANDLES.getIfPresent(((Number) ((Map<?, ?>) args[i]).get(HANDLE_KEY)).longValue());
				if (wrapper != null) {
					Object unwrapped = wrapper.getObject();
					args[i] = unwrapped instanceof ICCTypeWrapped ? ((ICCTypeWrapped) unwrapped).getObject() : unwrapped;
				}
			}
		}
		return args;
	}

	private static boolean argumentsMatch(Method method, Object[] arguments) {
		Class<?>[] params = method.getParameterTypes();
		if (arguments.length < params.length) {
			return false;
		}
		for (int i = 0; i < params.length; i++) {
			if (arguments[i] == null || !params[i].isAssignableFrom(arguments[i].getClass())) {
				return false;
			}
		}
		return true;
	}

	private String noSuchMethod(String name) {
		StringBuilder error = new StringBuilder("No such method.");
		for (Method method : info.commands.values()) {
			if (!method.getName().equalsIgnoreCase(name)) {
				continue;
			}
			error.append("\n").append(method.getName()).append("(");
			Class<?>[] params = method.getParameterTypes();
			for (int i = 0; i < params.length; i++) {
				error.append(i > 0 ? ", " : "").append(params[i].getSimpleName());
			}
			error.append(")");
		}
		return error.toString();
	}

	private Object help(Object[] arguments) {
		StringBuilder help = new StringBuilder();
		StringBuilder head = new StringBuilder();
		StringBuilder head2 = new StringBuilder();
		head.append("Type: ");
		head.append(info.type);
		head.append("\n");
		head2.append("Commands: \n");
		for (Integer num : info.commands.keySet()) {
			Method method = info.commands.get(num);
			StringBuilder command = new StringBuilder();
			if (help.length() != 0) {
				command.append("\n");
			}
			int number = num;
			if (number < 10) {
				command.append(" ");
			}
			command.append(number);
			command.append(" ");
			command.append(method.isAnnotationPresent(CCDirectCall.class) ? "D" : " ");
			command.append(method.isAnnotationPresent(CCQueued.class) ? "Q" : " ");
			command.append(": ");
			command.append(method.getName());
			StringBuilder param = new StringBuilder();
			param.append("(");
			boolean a = false;
			for (Class<?> clazz : method.getParameterTypes()) {
				if (a) {
					param.append(", ");
				}
				param.append(clazz.getSimpleName());
				a = true;
			}
			param.append(")");
			if (param.length() + command.length() > 36) {
				command.append("\n      ---");
			}
			command.append(param);
			help.append(command);
		}
		String[] lines = help.toString().split("\n");
		if (lines.length > 16) {
			int pageNumber = 1;
			if (arguments.length > 0 && arguments[0] instanceof Double) {
				pageNumber = Math.max(1, (int) Math.floor((Double) arguments[0]));
			}
			StringBuilder page = new StringBuilder();
			page.append(head);
			page.append("Page ");
			page.append(pageNumber);
			page.append(" of ");
			page.append(lines.length / 10 + (lines.length % 10 == 0 ? 0 : 1));
			page.append("\n");
			page.append(head2);
			pageNumber--;
			int from = pageNumber * 11;
			int to = pageNumber * 11 + 11;
			for (int i = from; i < to; i++) {
				if (i < lines.length) {
					page.append(lines[i]);
				}
				if (i < to - 1) {
					page.append("\n");
				}
			}
			return page.toString();
		}
		for (int i = 0; i < 16 - lines.length; i++) {
			head.insert(0, "\n");
		}
		return String.format("%s%s%s", head, head2, help);
	}

	private Object helpCommand(Object[] arguments) {
		if (arguments.length != 1) {
			return "Wrong Argument Count";
		}
		if (!(arguments[0] instanceof Double)) {
			return "Wrong Argument Type";
		}
		Integer number = (int) Math.floor((Double) arguments[0]);
		if (!info.commands.containsKey(number)) {
			return "No command with that index";
		}
		Method method = info.commands.get(number);
		StringBuilder help = new StringBuilder();
		help.append("---------------------------------\n");
		help.append("Command: ");
		help.append(method.getName());
		help.append("\n");
		help.append("Parameter: ");
		if (method.getParameterTypes().length > 0) {
			help.append("\n");
			boolean a = false;
			for (Class<?> clazz : method.getParameterTypes()) {
				if (a) {
					help.append(", ");
				}
				help.append(clazz.getSimpleName());
				a = true;
			}
			help.append("\n");
		} else {
			help.append("NONE\n");
		}
		help.append("Return Type: ");
		help.append(method.getReturnType().getName());
		help.append("\n");
		help.append("Description: \n");
		help.append(method.getAnnotation(CCCommand.class).description());
		return help.toString();
	}
}
