/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.UidGenerator;
import android.text.format.Time;
import android.util.Log;
import at.bitfire.davdroid.Constants;

public class Event extends Resource {
	public enum TYPE{
		VEVENT,VTODO,UNKNOWN;
	}
	
	private final static String TAG = "davdroid.Event";

	private TimeZoneRegistry tzRegistry;

	@Getter	@Setter	private String summary, location, description;

	@Getter	private DtStart dtStart;
	@Getter	private DtEnd dtEnd;
	@Getter	@Setter	private RDate rdate;
	@Getter	@Setter	private RRule rrule;
	@Getter	@Setter	private ExDate exdate;
	@Getter	@Setter	private ExRule exrule;

	@Getter	@Setter	private Boolean forPublic;
	@Getter	@Setter	private Status status;

	@Getter	@Setter	private TYPE type;

	@Getter	@Setter	private Organizer organizer;
	@Getter	private List<Attendee> attendees = new LinkedList<Attendee>();

	@Getter	@Setter private Priority priority;

	@Getter	@Setter private PercentComplete completed;

	@Getter	@Setter private Due due;

	public void addAttendee(Attendee attendee) {
		attendees.add(attendee);
	}

	public Event(String name, String ETag, TYPE type) {
		super(name, ETag);
		
		DefaultTimeZoneRegistryFactory factory = new DefaultTimeZoneRegistryFactory();
		tzRegistry = factory.createRegistry();
		this.type=type;
	}

	public Event(long localID, String name, String ETag, TYPE type) {
		this(name, ETag,type);
		this.localID = localID;
	}

	@Override
	public void parseEntity(@NonNull InputStream entity) throws IOException,
			ParserException {
		CalendarBuilder builder = new CalendarBuilder();
		net.fortuna.ical4j.model.Calendar ical = builder.build(entity);
		if (ical == null)
			return;

		
		ComponentList events = ical.getComponents(Component.VEVENT);
		if (events.size() > 0) {
			// event
			parseEvent(events);
			type = TYPE.VEVENT;
		} else {
			ComponentList todos = ical.getComponents(Component.VTODO);
			if (todos.size() > 0) {
				//task
				parseTodo(todos);
				type = TYPE.VTODO;
			} else {
				Log.wtf(TAG, "unkown component type");
			}
		}
		Log.i(TAG, "Parsed iCal: " + ical.toString());
	}

	private void parseTodo(ComponentList todos) throws SocketException {

		if (todos == null || todos.isEmpty())
			return;

		VToDo todo = (VToDo) todos.get(0);

		if (todo.getUid() != null)
			uid = todo.getUid().toString();
		else {
			Log.w(TAG, "Received VEVENT without UID, generating new one");
			UidGenerator uidGenerator = new UidGenerator(
					Integer.toString(android.os.Process.myPid()));
			uid = uidGenerator.generateUid().getValue();
		}

		dtStart = todo.getStartDate();
		validateTimeZone(dtStart);

		rrule = (RRule) todo.getProperty(Property.RRULE);
		rdate = (RDate) todo.getProperty(Property.RDATE);
		exrule = (ExRule) todo.getProperty(Property.EXRULE);
		exdate = (ExDate) todo.getProperty(Property.EXDATE);

		if (todo.getSummary() != null)
			summary = todo.getSummary().getValue();
		if (todo.getLocation() != null)
			location = todo.getLocation().getValue();
		if (todo.getDescription() != null)
			description = todo.getDescription().getValue();

		status = todo.getStatus();

		organizer = todo.getOrganizer();
		for (Object o : todo.getProperties(Property.ATTENDEE))
			attendees.add((Attendee) o);

		Clazz classification = todo.getClassification();
		if (classification != null) {
			if (classification == Clazz.PUBLIC)
				forPublic = true;
			else if (classification == Clazz.CONFIDENTIAL
					|| classification == Clazz.PRIVATE)
				forPublic = false;
		}
		
		priority=todo.getPriority();
		completed=todo.getPercentComplete();
		due=todo.getDue();
		Log.d(TAG,"parsed VTODO");

	}

	private void parseEvent(ComponentList events) throws SocketException {
		if (events == null || events.isEmpty())
			return;
		VEvent event = (VEvent) events.get(0);

		if (event.getUid() != null)
			uid = event.getUid().toString();
		else {
			Log.w(TAG, "Received VEVENT without UID, generating new one");
			UidGenerator uidGenerator = new UidGenerator(
					Integer.toString(android.os.Process.myPid()));
			uid = uidGenerator.generateUid().getValue();
		}

		dtStart = event.getStartDate();
		validateTimeZone(dtStart);
		dtEnd = event.getEndDate();
		validateTimeZone(dtEnd);

		rrule = (RRule) event.getProperty(Property.RRULE);
		rdate = (RDate) event.getProperty(Property.RDATE);
		exrule = (ExRule) event.getProperty(Property.EXRULE);
		exdate = (ExDate) event.getProperty(Property.EXDATE);

		if (event.getSummary() != null)
			summary = event.getSummary().getValue();
		if (event.getLocation() != null)
			location = event.getLocation().getValue();
		if (event.getDescription() != null)
			description = event.getDescription().getValue();

		status = event.getStatus();

		organizer = event.getOrganizer();
		for (Object o : event.getProperties(Property.ATTENDEE))
			attendees.add((Attendee) o);

		Clazz classification = event.getClassification();
		if (classification != null) {
			if (classification == Clazz.PUBLIC)
				forPublic = true;
			else if (classification == Clazz.CONFIDENTIAL
					|| classification == Clazz.PRIVATE)
				forPublic = false;
		}
	}

	@Override
	public String toEntity() {
		net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
		ical.getProperties().add(
				new ProdId("-//bitfire web engineering//DAVdroid "
						+ Constants.APP_VERSION + "//EN"));
		ical.getProperties().add(Version.VERSION_2_0);
		if(type==TYPE.VEVENT){
			fromEvent(ical);
		}else if(type==TYPE.VTODO){
			fromToDo(ical);
		}

		/*
		 * if (dtStart.getTimeZone() != null)
		 * ical.getComponents().add(dtStart.getTimeZone().getVTimeZone()); if
		 * (dtEnd.getTimeZone() != null)
		 * ical.getComponents().add(dtEnd.getTimeZone().getVTimeZone());
		 */

		return ical.toString();
	}

	private void fromToDo(net.fortuna.ical4j.model.Calendar ical) {
		VToDo todo = new VToDo();
		PropertyList props = todo.getProperties();

		addComonProps(props);

		if (forPublic != null)
			todo.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);
		
		props.add(priority);
		if(due!=null)
			props.add(due);
		if(completed!=null)
			props.add(completed);

		ical.getComponents().add(todo);
		
	}

	private void addComonProps(PropertyList props) {
		if (uid != null)
			props.add(new Uid(uid));
		
		if(dtStart!=null)
			props.add(dtStart);
		if (rrule != null)
			props.add(rrule);
		if (rdate != null)
			props.add(rdate);
		if (exrule != null)
			props.add(exrule);
		if (exdate != null)
			props.add(exdate);

		if (summary != null)
			props.add(new Summary(summary));
		if (location != null)
			props.add(new Location(location));
		if (description != null)
			props.add(new Description(description));

		if (status != null)
			props.add(status);

		if (organizer != null)
			props.add(organizer);
		for (Attendee attendee : attendees)
			props.add(attendee);
	}

	private void fromEvent(net.fortuna.ical4j.model.Calendar ical) {
		VEvent event = new VEvent();
		PropertyList props = event.getProperties();

		if (uid != null)
			props.add(new Uid(uid));

		
		props.add(dtEnd);
		addComonProps(props);

		if (forPublic != null)
			event.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);

		ical.getComponents().add(event);
	}

	public long getDtStartInMillis() {
	
		return dtStart==null?0:dtStart.getDate().getTime();
	}

	public String getDtStartTzID() {
		return getTzId(dtStart);
	}

	public void setDtStart(long tsStart, String tzID) {
		if (tzID == null) { // all-day
			dtStart = new DtStart(new Date(tsStart));
		} else {
			DateTime start = new DateTime(tsStart);
			start.setTimeZone(tzRegistry.getTimeZone(tzID));
			dtStart = new DtStart(start);
		}
	}
	
	public void setDue(long tsDue, String tzID) {
		if (tzID == null) { // all-day
			due = new Due(new Date(tsDue));
		} else {
			DateTime due = new DateTime(tsDue);
			due.setTimeZone(tzRegistry.getTimeZone(tzID));
			this.due = new Due(due);
		}
	}

	public long getDtEndInMillis() {
		if (hasNoTime(dtStart) && dtEnd == null) {
			// dtEnd = dtStart + 1 day
			Calendar c = Calendar.getInstance(TimeZone
					.getTimeZone(Time.TIMEZONE_UTC));
			c.setTime(dtStart.getDate());
			c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}

		return dtEnd.getDate().getTime();
	}

	public String getDtEndTzID() {
		return getTzId(dtEnd);
	}

	public void setDtEnd(long tsEnd, String tzID) {
		if (tzID == null) { // all-day
			dtEnd = new DtEnd(new Date(tsEnd));
		} else {
			DateTime end = new DateTime(tsEnd);
			end.setTimeZone(tzRegistry.getTimeZone(tzID));
			dtEnd = new DtEnd(end);
		}
	}

	// helpers

	public boolean isAllDay() {
		if (hasNoTime(dtStart)) {
			// events on that day
			if (dtEnd == null)
				return true;

			// all-day events
			if (hasNoTime(dtEnd))
				return true;
		}
		return false;
	}

	protected boolean hasNoTime(DateProperty date) {
		if(date==null)
			return true;
		return !(date.getDate() instanceof DateTime);
	}

	String getTzId(DateProperty date) {
		if (date == null)
			return null;

		if (hasNoTime(date) || date.isUtc())
			return Time.TIMEZONE_UTC;
		else if (date.getTimeZone() != null)
			return date.getTimeZone().getID();
		else if (date.getParameter(Value.TZID) != null)
			return date.getParameter(Value.TZID).getValue();
		return null;
	}

	/* guess matching Android timezone ID */
	protected void validateTimeZone(DateProperty date) {
		if (date==null||date.isUtc() || hasNoTime(date))
			return;

		String tzID = getTzId(date);
		if (tzID == null)
			return;

		String localTZ = Time.TIMEZONE_UTC;

		String availableTZs[] = SimpleTimeZone.getAvailableIDs();
		for (String availableTZ : availableTZs)
			if (tzID.indexOf(availableTZ, 0) != -1) {
				localTZ = availableTZ;
				break;
			}

		Log.i(TAG, "Assuming time zone " + localTZ + " for " + tzID);
		date.setTimeZone(tzRegistry.getTimeZone(localTZ));
	}

	@Override
	public void validate() throws ValidationException {
		super.validate();

		if (dtStart == null)
			throw new ValidationException("dtStart must not be empty");
	}

	public long getDueInMillis() {
		if (hasNoTime(due) && due == null) {
			// dtEnd = dtStart + 1 day
			Calendar c = Calendar.getInstance(TimeZone
					.getTimeZone(Time.TIMEZONE_UTC));
			c.setTime(due.getDate());
			c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}

		return due.getDate().getTime();
	}
}
