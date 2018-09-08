package state;

import delta.Cancellation;
import delta.Placement;
import delta.Side;
import delta.Trade;


public interface OrderBook extends State{
    LimitOrderBook place(Placement placement);
    LimitOrderBook cancel(Cancellation cancellation);
}
