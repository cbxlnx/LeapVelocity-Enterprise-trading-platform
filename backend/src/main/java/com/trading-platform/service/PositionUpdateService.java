import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PositionUpdateService {

	private final Map<String, Position> positionsByAccountAndSymbol;

	public PositionUpdateService() {
		this.positionsByAccountAndSymbol = new HashMap<>();
	}

	public void addPosition(Position position) {
		String key;

		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		key = positionKey(position.getAccountId(), position.getSymbol());
		positionsByAccountAndSymbol.put(key, position);
	}

	public Position getPosition(Long accountId, String symbol) {
		return positionsByAccountAndSymbol.get(positionKey(accountId, symbol));
	}

	public List<Position> getPositionsForAccount(Long accountId) {
		List<Position> positions;

		positions = new ArrayList<>();
		for (Position position : positionsByAccountAndSymbol.values()) {
			if (position.getAccountId() != null && position.getAccountId().equals(accountId)) {
				positions.add(position);
			}
		}

		return positions;
	}

	public void applyBuy(Order order) {
		Position position;
		String key;

		key = positionKey(order.getAccountId(), order.getSymbol());
		position = positionsByAccountAndSymbol.get(key);

		if (position == null) {
			position = new Position(order.getAccountId(), order.getSymbol(), order.getQuantity(), order.getPrice());
			positionsByAccountAndSymbol.put(key, position);
			return;
		}

		position.apply(order.getQuantity(), order.getPrice());
	}

	public void applySell(Order order) {
		Position position;
		String key;
		BigDecimal remainingQuantity;

		key = positionKey(order.getAccountId(), order.getSymbol());
		position = positionsByAccountAndSymbol.get(key);
		if (position == null) {
			throw new InsufficientHoldingsException(order.getAccountId(), order.getSymbol(), order.getQuantity(), BigDecimal.ZERO);
		}

		if (position.getQuantity().compareTo(order.getQuantity()) < 0) {
			throw new InsufficientHoldingsException(order.getAccountId(), position.getSymbol(), order.getQuantity(), position.getQuantity());
		}

		remainingQuantity = position.getQuantity().subtract(order.getQuantity());
		if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			positionsByAccountAndSymbol.remove(key);
		} else {
			position.setQuantity(remainingQuantity);
		}
	}

	private String positionKey(Long accountId, String symbol) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Position symbol is required");
		}
		return accountId + "|" + symbol.trim();
	}
}