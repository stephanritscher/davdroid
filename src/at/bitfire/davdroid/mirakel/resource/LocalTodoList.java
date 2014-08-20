package at.bitfire.davdroid.mirakel.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.Status;

import org.apache.commons.lang.StringUtils;
import org.dmfs.provider.tasks.TaskContract;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Cleanup;
import lombok.Getter;

public class LocalTodoList extends LocalCollection<ToDo> {

    private static final String TAG="LocalTodoList";
    private long id;

    protected static String COLLECTION_COLUMN_CTAG = TaskContract.TaskLists.SYNC1;


    public LocalTodoList(Account account, ContentProviderClient providerClient, long id, String url,Context ctx) throws RemoteException {
        super(account, providerClient,ctx);
        this.id = id;
        this.url = url;
    }
    public static void create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info,Context ctx) throws LocalStorageException {
        ContentProviderClient client = resolver.acquireContentProviderClient(listsUri(account,ctx));
        if (client == null)
            throw new LocalStorageException("No Calendar Provider found (Calendar app disabled?)");

        int color = 0xFFC3EA6E;		// fallback: "DAVdroid green"
        if (info.getColor() != null) {
            Pattern p = Pattern.compile("#(\\p{XDigit}{6})(\\p{XDigit}{2})?");
            Matcher m = p.matcher(info.getColor());
            if (m.find()) {
                int color_rgb = Integer.parseInt(m.group(1), 16);
                int color_alpha = m.group(2) != null ? (Integer.parseInt(m.group(2), 16) & 0xFF) : 0xFF;
                color = (color_alpha << 24) | color_rgb;
            }
        }

        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskLists.SYNC_ENABLED, true);
        values.put(TaskContract.TaskLists.ACCOUNT_NAME, account.name);
        values.put(TaskContract.TaskLists.ACCOUNT_TYPE, account.type);
        values.put(TaskContract.TaskLists._SYNC_ID, info.getURL());
        values.put(TaskContract.TaskLists.LIST_NAME, info.getTitle());
        values.put(TaskContract.TaskLists.LIST_COLOR, color);
        //values.put(TaskContract.TaskLists.OWNER, account.name);
        values.put(TaskContract.TaskLists.VISIBLE, 1);
        //values.put(TaskContract.TaskLists.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_ALERT);

        if (info.isReadOnly())
            return;

        Log.i(TAG, "Inserting calendar: " + values.toString() + " -> " + listsUri(account,ctx).toString());
        try {
            client.insert(listsUri(account, ctx), values);
        } catch(RemoteException e) {
            throw new LocalStorageException(e);
        }
    }

    public static Uri listsUri(Account account, Context ctx) throws RecordNotFoundException {
        List<Uri> uris=todoURI(ctx,account, TaskContract.TaskLists.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }

    public static LocalTodoList[] findAll(Account account, ContentProviderClient providerClient,Context ctx) throws RemoteException, RecordNotFoundException {
        @Cleanup Cursor cursor = providerClient.query(listsUri(account, ctx),
                new String[] { TaskContract.TaskLists._ID, TaskContract.TaskLists._SYNC_ID },
                TaskContract.TaskLists.ACCOUNT_NAME+"=?", new String[]{account.name}, null);

        LinkedList<LocalTodoList> lists = new LinkedList<LocalTodoList>();
        while (cursor != null && cursor.moveToNext())
            lists.add(new LocalTodoList(account, providerClient, cursor.getInt(0), cursor.getString(1),ctx));
        return lists.toArray(new LocalTodoList[0]);
    }


    @Override
    protected Uri entriesURI() throws RecordNotFoundException{
        List<Uri> uris= todoURI(ctx, account, TaskContract.Tasks.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }
    @Override
    protected String entryColumnAccountType()	{ return TaskContract.Tasks.ACCOUNT_TYPE; }
    @Override
    protected String entryColumnAccountName()	{ return TaskContract.Tasks.ACCOUNT_NAME; }
    @Override
    protected String entryColumnParentID()		{ return TaskContract.Tasks.LIST_ID; }
    @Override
    protected String entryColumnID()			{ return TaskContract.Tasks._ID; }
    @Override
    protected String entryColumnRemoteName()	{ return TaskContract.Tasks._SYNC_ID; }
    @Override
    protected String entryColumnETag()			{ return TaskContract.Tasks.SYNC1; }
    @Override
    protected String entryColumnDirty()			{ return TaskContract.Tasks._DIRTY; }
    @Override
    protected String entryColumnDeleted()		{ return TaskContract.Tasks._DELETED; }
    @Override
    protected String entryColumnUID()			{ return TaskContract.Tasks.SYNC2; }

    @Getter
    protected String url, cTag;

    @Override
    public long getId(){
        return id;
    }


    @Override
    public void setCTag(String cTag) throws RecordNotFoundException {
        pendingOperations.add(ContentProviderOperation
                .newUpdate(ContentUris.withAppendedId(listsURI(), id))
                .withValue(COLLECTION_COLUMN_CTAG, cTag).build());
    }

    @Override
    public void populate(Resource record) throws LocalStorageException {
        @Cleanup Cursor cursor = ctx.getContentResolver().query(entriesURI(),
                new String[] {
					/* 0 */TaskContract.Tasks.TITLE, TaskContract.Tasks.LOCATION, TaskContract.Tasks.DESCRIPTION,
                        TaskContract.Tasks.DUE, TaskContract.Tasks.STATUS, TaskContract.Tasks.PRIORITY, entryColumnID(),
                        entryColumnAccountName(), entryColumnRemoteName(), entryColumnETag(), TaskContract.Tasks.PERCENT_COMPLETE },
                entryColumnID()+"=?", new String[]{String.valueOf(record.getLocalID())}, null);
        ToDo t=(ToDo)record;
        if (cursor != null && cursor.moveToFirst()) {
            t.setUid(cursor.getString(8));

            t.setSummary(cursor.getString(0));
            t.setLocation(cursor.getString(1));
            t.setDescription(cursor.getString(2));
            if (!cursor.isNull(3)){
                //Mirakel saves times in utc and transforms this dates to the current timezone
                t.setDue(cursor.getLong(3), TimeZone.getDefault().getID());
            }
            // status
            switch (cursor.getInt(4)) {
                case TaskContract.Tasks.STATUS_COMPLETED:
                    t.setStatus(Status.VTODO_COMPLETED);
                    t.setDateCompleted(new Completed(new DateTime(new Date())));
                    break;
                case TaskContract.Tasks.STATUS_CANCELLED:
                    t.setStatus(Status.VTODO_CANCELLED);
                    break;
                case TaskContract.Tasks.STATUS_IN_PROCESS:
                    t.setStatus(Status.VTODO_IN_PROCESS);
                    break;
                default:
                case TaskContract.Tasks.STATUS_NEEDS_ACTION:
                    t.setStatus(Status.VTODO_NEEDS_ACTION);
                    break;

            }
            t.setPriority(new Priority(cursor.getInt(5)));
            t.setCompleted(new PercentComplete(cursor.getInt(10)));
        }
    }

    @Override
    public ToDo newResource(long localID, String resourceName, String eTag) {
        return new ToDo(localID, resourceName, eTag);
    }

    @Override
    public void deleteAllExceptRemoteNames(Resource[] remoteResources) throws RecordNotFoundException {
        String where;
        if (remoteResources.length != 0) {
            List<String> sqlFileNames = new LinkedList<String>();
            for (Resource res : remoteResources) {
                sqlFileNames.add(DatabaseUtils.sqlEscapeString(res.getName()));
            }
            where = entryColumnRemoteName() + " NOT IN ("
                    + StringUtils.join(sqlFileNames, ",") + ")";
        } else {
            where = entryColumnRemoteName() + " IS NOT NULL";
        }
        ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(entriesURI())
                .withSelection(
                        entryColumnParentID() + "=? AND (" + where + ")",
                        new String[] { String.valueOf(id) });
        pendingOperations.add(builder.withYieldAllowed(true).build());
    }

    @Override
    protected ContentProviderOperation.Builder buildEntry(ContentProviderOperation.Builder builder, Resource resource, boolean insert) throws LocalStorageException {
        ToDo todo = (ToDo)resource;
        if(todo.getCreated()==null||todo.getUpdated()==null){
            Log.wtf(TAG,"somehow this task does not exists");
            return builder;
        }
        builder = builder.withValue(TaskContract.Tasks.TITLE, todo.getSummary())
                .withValue(TaskContract.Tasks.SYNC1, todo.getETag())
                .withValue(entryColumnUID(), todo.getUid())
                .withValue(TaskContract.Tasks.CREATED, todo.getCreated().getDate().getTime())
                .withValue(TaskContract.Tasks.LAST_MODIFIED, todo.getUpdated().getDate().getTime())
                .withValue(TaskContract.Tasks._SYNC_ID, todo.getName());
        if(insert){
            builder.withValue(TaskContract.Tasks.LIST_ID, id);
        }
        if(todo.getStatus()!=null&&todo.getDateCompleted()==null){
            Status status=todo.getStatus();
            if(status==Status.VTODO_CANCELLED){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_CANCELLED);
            }else if(status==Status.VTODO_COMPLETED){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_COMPLETED);
            }else if(status==Status.VTODO_IN_PROCESS){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_IN_PROCESS);
            }else if(status==Status.VTODO_NEEDS_ACTION){
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_NEEDS_ACTION);
            }else{
                builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_DEFAULT);
            }
        }else{
            builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_DEFAULT);
        }
        // .withValue(Tasks.U, value)//TODO uid??
        if (todo.getDue() != null) {
            builder = builder.withValue(TaskContract.Tasks.DUE, todo.getDueInMillis()).withValue(TaskContract.Tasks.IS_ALLDAY,1);
        }
        if (todo.getPriority() != null)
            builder = builder.withValue(TaskContract.Tasks.PRIORITY, todo.getPriority()
                    .getLevel());
        if (todo.getDescription() != null)
            builder = builder.withValue(TaskContract.Tasks.DESCRIPTION,
                    todo.getDescription());
        if(todo.getCompleted()!=null){
            builder.withValue(TaskContract.Tasks.PERCENT_COMPLETE, todo.getCompleted().getPercentage());
        }else{
            builder.withValue(TaskContract.Tasks.PERCENT_COMPLETE, 0);
        }
        if(todo.getDateCompleted()!=null){
            builder.withValue(TaskContract.Tasks.STATUS, TaskContract.Tasks.STATUS_COMPLETED);
        }
        return builder;
    }

    @Override
    protected void addDataRows(Resource resource, long localID, int backrefIdx) throws RecordNotFoundException {
        /*ToDo todo = (ToDo)resource;
        for (VAlarm alarm : todo.getAlarms())
            pendingOperations.add(buildReminder(
                    newDataInsertBuilder(alarmUri(),
                            TaskContract.Alarms., localID, backrefIdx), alarm)
                    .build());*/
    }

    @Override
    protected void removeDataRows(Resource resource) {
        //TODO
    }

    private Uri alarmUri() throws RecordNotFoundException {
        List<Uri> uris= todoURI(ctx, account, TaskContract.Alarms.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }

    protected static List<Uri> todoURI(final Context ctx,Account account,final String basePath) {
        List<Uri> uris = new ArrayList<Uri>();
        PackageManager pm = ctx.getPackageManager();
        try {
            PackageInfo mirakel = pm.getPackageInfo("de.azapps.mirakelandroid",
                    PackageManager.GET_PROVIDERS);
            if (mirakel != null && mirakel.versionCode > 18) {
                uris.add(Uri.parse("content://" + TaskContract.AUTHORITY + "/" + basePath)
                        .buildUpon()
                        .appendQueryParameter(TaskContract.ACCOUNT_NAME,
                                account.name)
                        .appendQueryParameter(TaskContract.ACCOUNT_TYPE,
                                account.type)
                        .appendQueryParameter(
                                TaskContract.CALLER_IS_SYNCADAPTER, "true")
                        .build());
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Mirakel not found");
        }catch (Exception e) {
            Log.wtf(TAG,Log.getStackTraceString(e));
        }
        try {
            PackageInfo dmfs = pm.getPackageInfo("org.dmfs.provider.tasks",
                    PackageManager.GET_PROVIDERS);
            if (dmfs != null) {
                uris.add(Uri.parse("content://" + TaskContract.AUTHORITY_DMFS + "/" + basePath)
                        .buildUpon()
                        .appendQueryParameter(TaskContract.ACCOUNT_NAME,
                                account.name)
                        .appendQueryParameter(TaskContract.ACCOUNT_TYPE,
                                account.type)
                        .appendQueryParameter(
                                TaskContract.CALLER_IS_SYNCADAPTER, "true")
                        .build());
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "dmfs not found");
        }
        if (uris.size() == 0) {
            //TODO show tost here
            //Toast.makeText(ctx, R.string.install_taskprovider,
            //		Toast.LENGTH_LONG).show();
        }
        return uris;
    }

    protected Uri listsURI() throws RecordNotFoundException {
        List<Uri> uris= todoURI(ctx, account, TaskContract.TaskLists.CONTENT_URI_PATH);
        if(uris.isEmpty()){
            throw  new RecordNotFoundException("No Taskprovider found");
        }
        return uris.get(0);
    }
}
