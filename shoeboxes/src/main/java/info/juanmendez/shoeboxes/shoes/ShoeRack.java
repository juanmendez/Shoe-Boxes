package info.juanmendez.shoeboxes.shoes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.juanmendez.shoeboxes.utils.ShoeUtils;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;


/**
 * Created by Juan Mendez on 6/7/2017.
 * www.juanmendez.info
 * contact@juanmendez.info
 */
public class ShoeRack {

    private ShoeModel shoeModel;
    private HashMap<String, String> requestActionMap = new HashMap<>();
    private List<String> history = new ArrayList<>();
    private PublishSubject<List<ShoeModel>> publishSubject = PublishSubject.create();
    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    public boolean  request( int requestId ){
        return request( requestId, "" );
    }

    public boolean request( int requestId, String action ){
        return request( Integer.toString( requestId), action );
    }

    public boolean request(String requestedTag) {
        return request( requestedTag, "");
    }

    public boolean request(String requestedTag, String action ) {

        /**
         * keep a reference of the action based on the tag in the shoeBox
         */
        ShoeBox shoeBox = search( requestedTag );

        if( shoeBox != null ){
            requestActionMap.put( shoeBox.getFragmentTag(), action );
        }

        return request( shoeBox );
    }

    public boolean request( ShoeBox shoeBox ){
        if( shoeBox != null ){

            String shoeTag = shoeBox.getFragmentTag();
            /*
            going forward can mean also steping back to a previous shoeModel
            lets find the shoeModel within..
            */
            ShoeModel parent = shoeBox.getParent();

            /**
             * for flow, we need to add in history previous siblings even if they are never visited.
             * this helps when switching to stack, we have in history those other siblings in order to go back.
             */

            //going forward can mean also stepping back to a previous shoeModel
            if( history.contains(shoeTag)){

                for( int i = history.size()-1; i>= 0; i--){

                    if( !history.get(i).equals( shoeTag ) ){
                        history.remove( i );
                    }else{
                        break;
                    }
                }

            }else{

                //if parent is a Flow, then also include previous siblings
                if( parent != null && parent instanceof ShoeFlow){

                    for( ShoeModel sibling: parent.getNodes() ){
                        if( sibling instanceof ShoeBox ){

                            if( sibling != shoeBox && !history.contains( ((ShoeBox) sibling).getFragmentTag() ) ){
                                history.add( ((ShoeBox) sibling).getFragmentTag() );
                            }else{
                                break;
                            }
                        }

                    }
                }

                history.add(shoeTag);
            }

            //lets not update based on a shoeBox which has no shoeFragment.
            if( shoeBox.getShoeFragmentAdapter() == null ){
                return false;
            }

            /**
             * shoeBox can be called twice. therefore we set the item
             * as deactivated and activated once again!
             */
            if( shoeBox.isActive() ){
                shoeBox.setActive(false);
                shoeBox.setActive(true);
            }else{
                publishSubject.onNext(ShoeUtils.getPath(shoeBox));
            }

            return true;
        }

        return false;
    }

    /**
     * try to include children in their parents, if there aren't any.
     * @param ids
     */
    public boolean suggest( int... ids ){
        String[] stringIds = new String[ids.length];

        for( int i = 0; i < ids.length; i++ ){
            stringIds[i] = Integer.toString( ids[i] );
        }

        return suggest( stringIds );
    }

    /**
     * try to include children in their parents, if there aren't any.
     * @param tags
     * @return  true if any of the tag was successfully executed.
     */
    public boolean suggest (String... tags ){

        ShoeBox shoeBox;
        ShoeModel shoeParent;
        Boolean anySuccess = false;

        for( String tag: tags ){

            shoeBox = search( tag );

            if( shoeBox != null  ){

                shoeParent = shoeBox.getParent();

                if( !ShoeUtils.anyChildActive( shoeParent ) ){
                    if( request( tag ) ){
                        anySuccess = true;
                    }
                }
            }
        }

        return anySuccess;
    }

    public boolean suggestIdsWithActions(HashMap<Integer, String> idsWithActions ){

        HashMap<String, String> tagsWithActions = new HashMap<>();

        for (Map.Entry<Integer, String> entry : idsWithActions.entrySet()) {
            tagsWithActions.put( Integer.toString(entry.getKey()), entry.getValue() );
        }

        return suggest( tagsWithActions );
    }

    public boolean suggest( HashMap<String,String> tagsWithActions ){

        ShoeModel shoeParent;
        ShoeBox shoeBox;
        Boolean anySuccess = false;

        for (Map.Entry<String, String> entry : tagsWithActions.entrySet()) {

            shoeBox = search( entry.getKey() );

            if( shoeBox != null  ){
                shoeParent = shoeBox.getParent();

                if( !ShoeUtils.anyChildActive( shoeParent ) ){
                    if( request( entry.getKey(), entry.getValue() ) ){
                        anySuccess = true;
                    }
                }
            }
        }

        return  anySuccess;
    }

    public void subscribe(Action1<List<ShoeModel>> action ){
        compositeSubscription.add( publishSubject.subscribe(action));
    }

    public List<String> getHistory() {
        return history;
    }

    public ShoeRack populate(ShoeModel... nodes) {

        boolean makeFlow = nodes.length > 1 || (nodes.length == 1 && nodes[0] instanceof ShoeBox );

        if( makeFlow ){
            shoeModel = ShoeFlow.build( nodes );
        }else{
            shoeModel = nodes[0];
        }

        replaceHistory();

        return this;
    }

    /**
     * When an app is rotated shoeModel can update all shoeBox references.
     * This method takes care of updating all history items to work along with the
     * new shoeBoxes.
     */
    private void replaceHistory(){
        
        //lets make sure to call back again for the last history registered..
        if( !history.isEmpty() ){

            //publishSubject.onNext(ShoeUtils.getPath(history.get(history.size()-1)));
            request( history.get(history.size()-1));
        }
    }

    public ShoeBox search(String tag) {
        return shoeModel.search( tag );
    }

    /**
     * Requeset to go back to the previous sibling before the last in history.
     * Read what happens when the last element is part of a ShoeFlow.
     * @return true if it handled back navigation; otherwise, it returns false.
     */
    public boolean goBack(){

        /**
         * last navNode can have an internal history;therefore,
         * rather than just jumping to the previous element it can
         * return false if it is done with its history.
         */

        //we always go to the element before the last
        Boolean executed = false;
        if( history.size() >= 2 ){

            String latestTag = history.get( history.size()-1 );
            String previousTag = history.get( history.size()-2 );
            ShoeBox latestShoeBox = search(latestTag);
            ShoeModel parent = latestShoeBox.getParent();
            ShoeModel grandParent = parent.getParent();


            /**
             * If currently we are on a ShoeFlow, we need to move out of all other parent siblings
             * As we don't navigate through a ShoeFlow, but only through a ShoeStack.
             *
             * The first child of a stack whose parent is a flow should be skipped.. (second condition)
             * Otherwise we request to go to the element before the last.
             */
            if( parent != null ){

                if( parent instanceof ShoeFlow ){
                    for( int i = history.size()-2; i>= 0; i--){

                        if( search(history.get(i)).getParent() != parent ){
                            return request( history.get(i), requestActionMap.get(history.get(i)));
                        }
                    }

                    //it never made the call
                    return false;
                }
                else
                if( grandParent != null ){

                    if( parent instanceof ShoeStack && grandParent instanceof ShoeFlow ){
                        if( ShoeUtils.anyOtherSiblingsInHistory(latestShoeBox)){
                            history.remove( latestTag );
                            return goBack();
                        }
                    }
                }
            }

            return request( previousTag, requestActionMap.get(previousTag) );
        }

        return false;
    }

    /**
     * While testing clearning has been convinient to start new tests.
     * This gives flexibility to an application to remove all history .
     */
    public void clearHistory(){
        history.clear();
        requestActionMap.clear();
    }


    public String getActionByTag( String tag ){
        return requestActionMap.get( tag );
    }

    public String getActionById( int id ){
        return getActionByTag( Integer.toString(id));
    }

    public boolean isHistoryEmpty(){
        return history.isEmpty();
    }

    /**
     * Activity requests shoeRack to set all shoeModels to be set to inactive
     * because the activity has ended or device is about to rotate
     */
    public void onRotation(){
        shoeModel.onRotation();
    }
}