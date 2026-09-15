package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamLoginTicketRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 测试用票据仓储：复刻「读一次即删」的一次性语义。 */
public class FakeTicketRepository implements EduroamLoginTicketRepository {

    private final Map<String, EduroamLoginTicket> tickets = new LinkedHashMap<>();

    @Override
    public EduroamLoginTicket save(EduroamLoginTicket ticket) {
        tickets.put(ticket.id(), ticket);
        return ticket;
    }

    @Override
    public Optional<EduroamLoginTicket> consume(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tickets.remove(id.trim()));
    }

    @Override
    public int purgeExpired(long now) {
        int purged = 0;
        for (Map.Entry<String, EduroamLoginTicket> entry : Map.copyOf(tickets).entrySet()) {
            if (entry.getValue().expired(now)) {
                tickets.remove(entry.getKey());
                purged++;
            }
        }
        return purged;
    }

    @Override
    public long count() {
        return tickets.size();
    }
}
