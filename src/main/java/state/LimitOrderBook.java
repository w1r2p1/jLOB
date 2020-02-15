package state;

import dto.Cancellation;
import dto.Placement;
import dto.Side;
import exceptions.jLOBException;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparators;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static java.math.BigDecimal.valueOf;

/**
 * L3 Limit Order Book implementation.
 *
 * Bids and offers are kept sorted in reverse natural and natural orders respectively
 * by the virtue of {@code Long2ObjectRBTreeMap<Limit>}s
 *
 * {@code Limit}s represent price levels in an order book and wrap around a collection of {@code Placement}s.
 *
 */

public class LimitOrderBook {

    private Long2ObjectRBTreeMap<Limit> bids;
    private Long2ObjectRBTreeMap<Limit> offers;
    private transient Long2ObjectOpenHashMap<Placement> placements;

    private LimitOrderBook(){
        this.bids = new Long2ObjectRBTreeMap<>(LongComparators.OPPOSITE_COMPARATOR);
        this.offers = new Long2ObjectRBTreeMap<>(LongComparators.NATURAL_COMPARATOR);
        this.placements = new Long2ObjectOpenHashMap<>();
    }

    public static LimitOrderBook empty(){
        return new LimitOrderBook();
    }

    public boolean isEmpty(){
        return bids.isEmpty() && offers.isEmpty() && placements.isEmpty();
    }

    public void place(Placement placement) {
        if (placements.containsKey(placement.getId()))
            return;
        if (placement.getSide() == Side.BID)
            bid(placement);
        else
            offer(placement);
    }

    private void bid(Placement placement) {
        long remainingQuantity = placement.getSize();
        Limit limit  = getBestLimit(offers);
        while (remainingQuantity > 0 && limit != null && limit.getPrice() <= placement.getPrice()) {
            remainingQuantity = limit.match(placement, placements);
            if (limit.isEmpty())
                offers.remove(limit.getPrice());
            limit = getBestLimit(offers);
        }
        if (remainingQuantity > 0) {
            placements.put(placement.getId(), place(bids, placement));
        }
    }

    private void offer(Placement placement) {
        long remainingQuantity = placement.getSize();
        Limit limit = getBestLimit(bids);
        while (remainingQuantity > 0 && limit != null && limit.getPrice() >= placement.getPrice()) {
            remainingQuantity = limit.match(placement, placements);
            if (limit.isEmpty())
                bids.remove(limit.getPrice());
            limit = getBestLimit(bids);
        }
        if (remainingQuantity > 0) {
            placements.put(placement.getId(), place(offers, placement));
        }
    }

    private Placement place(Long2ObjectRBTreeMap<Limit> levels, Placement placement) {
        Limit level = levels.get(placement.getPrice());
        if (level == null) {
            level = new Limit(placement.getSide(), placement.getPrice());
            levels.put(placement.getPrice(), level);
        }
        return level.place(placement);
    }

    public void cancel(Cancellation cancellation) {
        Placement placement = placements.get(cancellation.getId());
        if (placement == null || cancellation.getSize() > placement.getSize())
            throw new jLOBException("Placement does not exist or cancellation size is inappropriate");
        if (cancellation.getSize() == placement.getSize()) {
            remove(placement);
            placements.remove(placement.getId());
        } else {
            placement.reduce(cancellation.getSize());
        }
    }

    private void remove(Placement placement) {
        Side side = placement.getSide();
        long price = placement.getPrice();
        Limit limit;

        if (side == Side.BID)
            limit = bids.get(price);
        else
            limit = offers.get(price);

        limit.remove(placement);
        if (limit.isEmpty())
            remove(limit);
    }

    private void remove(Limit limit) {
        if (limit.getSide() == Side.BID)
            bids.remove(limit.getPrice());
        else
            offers.remove(limit.getPrice());
    }

    private Limit getBestLimit(Long2ObjectRBTreeMap<Limit> levels) {
        if (levels.isEmpty())
            return null;
        return levels.get(levels.firstLongKey());
    }

    public long getMidPrice(){
        return (bids.firstLongKey() + offers.firstLongKey()) / 2;
    }

    public Stream<Long2ObjectMap.Entry<Limit>> streamBids() {
        return bids.long2ObjectEntrySet().stream();
    }

    public Stream<Long2ObjectMap.Entry<Limit>> streamOffers() {
        return offers.long2ObjectEntrySet().stream();
    }

    public long getBestBid(){
        return bids.firstLongKey();
    }

    public long getBestOffer(){
        return offers.firstLongKey();
    }

    public long getBestBidAmount(){
        return bids.get(bids.firstLongKey()).getVolume();
    }

    public long getBestOfferAmount(){
        return offers.get(offers.firstLongKey()).getVolume();
    }

    private BigDecimal getAveragePrice(long size, Long2ObjectRBTreeMap<Limit> levels) {
        long psizesum = 0L, sizesum = 0L;
        for(Limit limit : levels.values()) {
            long unfilled_size = size - sizesum;
            long price = limit.getPrice();
            long volume = limit.getVolume();
            long s = Math.min(unfilled_size, volume);
            sizesum += s;
            psizesum += s * price;
            if(sizesum >= size)
                return valueOf(sizesum).equals(BigDecimal.ZERO) ? BigDecimal.ZERO :
                        valueOf(psizesum).divide(valueOf(size), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getAverageSalePrice(long size){
        return getAveragePrice(size, bids);
    }

    public BigDecimal getAveragePurchasePrice(long size){
        return getAveragePrice(size, offers);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("bids", bids)
                .append("offers", offers)
                .toString();
    }

    public String bestBidOffer() {
        StringBuilder builder = new StringBuilder();
        builder.append("| Timestamp: " + System.nanoTime());
        builder.append(
                "| Best Bid Price: " + bids.firstLongKey() +
                "| Best Bid Volume: " + bids.get(bids.firstLongKey()).getVolume() +
                "| Best Offer Price: " + offers.firstLongKey() +
                "| Best Offer Volume: " + offers.get(offers.firstLongKey()).getVolume()
        );
        return builder.toString();
    }
}