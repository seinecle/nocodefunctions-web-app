package net.clementlevallois.nocodeapp.web.front.flows;


import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;

@Named
@SessionScoped
public class WorkflowSessionBean implements Serializable {

    private CowoState cowoState;
    private TopicsState topicsState;

    public CowoState getCowoState() {
        return cowoState;
    }

    public void setCowoState(CowoState cowoState) {
        this.cowoState = cowoState;
    }

    public TopicsState getTopicsState() {
        return topicsState;
    }

    public void setTopicsState(TopicsState topicsState) {
        this.topicsState = topicsState;
    }
}
