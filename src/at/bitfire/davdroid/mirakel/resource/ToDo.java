package at.bitfire.davdroid.mirakel.resource;

import android.text.format.Time;
import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
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
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import org.dmfs.provider.tasks.TaskContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import at.bitfire.davdroid.mirakel.Constants;
import at.bitfire.davdroid.mirakel.syncadapter.DavSyncAdapter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ToDo extends Resource {
    private final static String TAG="ToDo";
    private final static TimeZoneRegistry tzRegistry = new DefaultTimeZoneRegistryFactory().createRegistry();

    @Getter
    @Setter
    private String summary, location, description;

    @Getter	private DtStart dtStart;
    @Getter	@Setter	private RDate rdate;
    @Getter	@Setter	private RRule rrule;
    @Getter	@Setter	private ExDate exdate;
    @Getter	@Setter	private ExRule exrule;
    @Getter @Setter private Created created;
    @Getter @Setter private LastModified updated;

    @Getter	@Setter	private Boolean forPublic;
    @Getter	@Setter	private Status status;

    @Getter @Setter private boolean opaque;
    @Getter @Setter private Completed dateCompleted;


    @Getter @Setter private Organizer organizer;
    @Getter private List<Attendee> attendees = new LinkedList<Attendee>();

    public void addAttendee(Attendee attendee) {
        attendees.add(attendee);
    }

    @Getter private List<VAlarm> alarms = new LinkedList<VAlarm>();
    public void addAlarm(VAlarm alarm) {
        alarms.add(alarm);
    }

    @Getter	@Setter private Priority priority;

    @Getter	@Setter private PercentComplete completed;

    @Getter	@Setter private Due due;

    public ToDo(String name, String ETag){
        super(name,ETag);
    }

    public ToDo(long localID, String name, String ETag){
        super(localID,name,ETag);
    }

    @Override
    public void initialize() {
        generateUID();
        name = uid.replace("@", "_") + ".ics";
    }



    protected void generateUID() {
        UidGenerator generator = new UidGenerator(new SimpleHostInfo(DavSyncAdapter.getAndroidID()), String.valueOf(android.os.Process.myPid()));
        uid = generator.generateUid().getValue();
    }

    @Override
    public void parseEntity(@NonNull InputStream entity) throws IOException, InvalidResourceException {
        net.fortuna.ical4j.model.Calendar ical;
        try {
            CalendarBuilder builder = new CalendarBuilder();
            ical = builder.build(entity);

            if (ical == null)
                throw new InvalidResourceException("No iCalendar found");
        } catch (ParserException e) {
            throw new InvalidResourceException(e);
        }
        // ToDo
        ComponentList todos = ical.getComponents(Component.VTODO);
        if (todos == null || todos.isEmpty())
            throw new InvalidResourceException("Maybe VEVENT or so");

        VToDo todo = (VToDo) todos.get(0);
        if (todo.getUid() != null)
            uid = todo.getUid().toString();
        else {
            Log.w(TAG, "Received VTODO without UID, generating new one");
            generateUID();
        }

        if(todo.getStartDate()!=null) {
            dtStart = todo.getStartDate();
            Event.validateTimeZone(dtStart);
        }

        rrule = (RRule) todo.getProperty(Property.RRULE);
        rdate = (RDate) todo.getProperty(Property.RDATE);
        exrule = (ExRule) todo.getProperty(Property.EXRULE);
        exdate = (ExDate) todo.getProperty(Property.EXDATE);
        dateCompleted=todo.getDateCompleted();

        if(todo.getCreated()!=null){
            created=todo.getCreated();
        }else{
            created=new Created();
            created.setDate(new Date(new java.util.Date()));
        }

        if(todo.getLastModified()!=null){
            updated=todo.getLastModified();
        }else{
            updated=new LastModified();
            updated.setDate(new Date(new java.util.Date()));
        }

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
        if(todo.getDue()!=null) {
            due = todo.getDue();
            Event.validateTimeZone(due);
        }
        Log.d(TAG,"parsed VTODO");
        alarms=todo.getAlarms();


        Log.i(TAG, "Parsed iCal: " + ical.toString());
    }





    @Override
    public ByteArrayOutputStream toEntity() throws IOException {
        net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
        ical.getProperties().add(Version.VERSION_2_0);
        ical.getProperties().add(new ProdId("-//bitfire web engineering//DAVdroid " + Constants.APP_VERSION + "//EN"));
        ByteArrayOutputStream os=null;
        VToDo todo = new VToDo();
        PropertyList props = todo.getProperties();
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
        if (summary != null && !summary.isEmpty())
            props.add(new Summary(summary));
        if (location != null && !location.isEmpty())
            props.add(new Location(location));
        if (description != null && !description.isEmpty())
            props.add(new Description(description));

        if (status != null)
            props.add(status);
        if (!opaque)
            props.add(Transp.TRANSPARENT);
        if (organizer != null)
            props.add(organizer);
        props.addAll(attendees);
        if (forPublic != null)
            todo.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);

        props.add(priority);
        if(due!=null)
            props.add(due);
        if(completed!=null)
            props.add(completed);
        if(dateCompleted!=null)
            props.add(dateCompleted);

        if(status.getValue().equals(Status.VTODO_COMPLETED)){
            props.add(new Completed(new DateTime(new java.util.Date())));
        }

        ical.getComponents().add(todo);
        try {
            CalendarOutputter output = new CalendarOutputter(false);
            os = new ByteArrayOutputStream();
            output.output(ical, os);
        } catch (ValidationException e) {
            Log.e(TAG, "Generated invalid iCalendar",e);
        }
        return os;
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

    protected static boolean hasTime(DateProperty date) {
        return date.getDate() instanceof DateTime;
    }

    public long getDueInMillis() {
        if (!hasTime(due) && due == null) {
            Calendar c = Calendar.getInstance(TimeZone
                    .getTimeZone(Time.TIMEZONE_UTC));
            c.setTime(due.getDate());
            c.add(Calendar.DATE, 1);
            return c.getTimeInMillis();
        }
        return due.getDate().getTime();
    }

}
